package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.currency.CurrencyRepository;
import com.pos_onlineshop.hybrid.dtos.CreateSupplierInvoiceRequest;
import com.pos_onlineshop.hybrid.dtos.SupplierInvoiceResponse;
import com.pos_onlineshop.hybrid.enums.FinancialEventType;
import com.pos_onlineshop.hybrid.enums.GLSourceModule;
import com.pos_onlineshop.hybrid.enums.SupplierInvoiceStatus;
import com.pos_onlineshop.hybrid.gl.FinancialEvent;
import com.pos_onlineshop.hybrid.journalEntry.JournalEntry;
import com.pos_onlineshop.hybrid.journalEntry.JournalEntryRepository;
import com.pos_onlineshop.hybrid.products.Product;
import com.pos_onlineshop.hybrid.products.ProductRepository;
import com.pos_onlineshop.hybrid.purchaseOrder.PurchaseOrder;
import com.pos_onlineshop.hybrid.purchaseOrder.PurchaseOrderRepository;
import com.pos_onlineshop.hybrid.shop.Shop;
import com.pos_onlineshop.hybrid.shop.ShopRepository;
import com.pos_onlineshop.hybrid.suppliers.Suppliers;
import com.pos_onlineshop.hybrid.suppliers.SuppliersRepository;
import com.pos_onlineshop.hybrid.supplierInvoice.SupplierInvoice;
import com.pos_onlineshop.hybrid.supplierInvoice.SupplierInvoiceRepository;
import com.pos_onlineshop.hybrid.supplierInvoiceLine.SupplierInvoiceLine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Accounts-Payable invoice lifecycle: DRAFT -> POSTED -> (PARTIALLY_PAID ->)* PAID, VOID
 * reachable from DRAFT or from POSTED with zero payments applied.
 *
 * GL posting on post() is conditional on whether the invoice references a PurchaseOrder:
 * goods received through PurchaseOrderService.receive() already posted STOCK_RECEIPT
 * (Dr Inventory / Cr Accounts Payable) at the moment of receipt - see ShopInventoryService.
 * Posting PURCHASE_INVOICE again for the same goods would double both the inventory value and
 * the AP liability. A PO-linked invoice is therefore a subledger record only (invoice number,
 * due date, payment terms against a liability already in the GL) and does not post a second
 * journal entry. Only a standalone invoice (no PurchaseOrder - a service/expense bill with
 * nothing previously received) posts PURCHASE_INVOICE.
 *
 * This is the same GRNI (goods-received-not-invoiced) simplification documented on the
 * STOCK_RECEIPT posting rule: no separate GRNI clearing account exists yet, so the receipt IS
 * the AP trigger for PO-linked purchases. A full GRNI model (receipt credits a GRNI clearing
 * account, invoice debits GRNI / credits AP) is a reasonable future enhancement, not
 * implemented here to avoid changing STOCK_RECEIPT's already-tested behaviour in this pass.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SupplierInvoiceService {

    private final SupplierInvoiceRepository supplierInvoiceRepository;
    private final SuppliersRepository suppliersRepository;
    private final ShopRepository shopRepository;
    private final CurrencyRepository currencyRepository;
    private final ProductRepository productRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final GLPostingService glPostingService;
    private final CurrencyService currencyService;

    @Transactional
    public SupplierInvoice createInvoice(CreateSupplierInvoiceRequest request) {
        if (supplierInvoiceRepository.existsByInvoiceNumber(request.getInvoiceNumber())) {
            throw new IllegalArgumentException("An invoice with number " + request.getInvoiceNumber() + " already exists");
        }
        Suppliers supplier = suppliersRepository.findById(request.getSupplierId())
                .orElseThrow(() -> new IllegalArgumentException("Supplier not found: " + request.getSupplierId()));
        Shop shop = shopRepository.findById(request.getShopId())
                .orElseThrow(() -> new IllegalArgumentException("Shop not found: " + request.getShopId()));
        Currency currency = currencyRepository.findById(request.getCurrencyId())
                .orElseThrow(() -> new IllegalArgumentException("Currency not found: " + request.getCurrencyId()));

        PurchaseOrder purchaseOrder = null;
        if (request.getPurchaseOrderId() != null) {
            purchaseOrder = purchaseOrderRepository.findById(request.getPurchaseOrderId())
                    .orElseThrow(() -> new IllegalArgumentException("Purchase order not found: " + request.getPurchaseOrderId()));
        }

        BigDecimal tax = request.getTaxAmount() != null ? request.getTaxAmount() : BigDecimal.ZERO;

        SupplierInvoice invoice = SupplierInvoice.builder()
                .invoiceNumber(request.getInvoiceNumber())
                .supplier(supplier)
                .shop(shop)
                .purchaseOrder(purchaseOrder)
                .currency(currency)
                .invoiceDate(request.getInvoiceDate())
                .dueDate(request.getDueDate())
                .subtotalAmount(request.getSubtotalAmount())
                .taxAmount(tax)
                .totalAmount(request.getSubtotalAmount().add(tax))
                .notes(request.getNotes())
                .build();

        if (request.getLines() != null) {
            for (CreateSupplierInvoiceRequest.Line lineRequest : request.getLines()) {
                Product product = productRepository.findById(lineRequest.getProductId())
                        .orElseThrow(() -> new IllegalArgumentException("Product not found: " + lineRequest.getProductId()));
                invoice.addLine(SupplierInvoiceLine.builder()
                        .product(product)
                        .quantity(lineRequest.getQuantity())
                        .unitCost(lineRequest.getUnitCost())
                        .build());
            }
        }

        SupplierInvoice saved = supplierInvoiceRepository.save(invoice);
        log.info("Created supplier invoice {} for supplier {}{}", saved.getInvoiceNumber(), supplier.getName(),
                purchaseOrder != null ? " (linked to PO " + purchaseOrder.getPoNumber() + ")" : "");
        return saved;
    }

    @Transactional
    public SupplierInvoice postInvoice(Long invoiceId) {
        SupplierInvoice invoice = findOrThrow(invoiceId);
        invoice.post();
        SupplierInvoice saved = supplierInvoiceRepository.save(invoice);

        if (saved.isPoLinked()) {
            log.info("Supplier invoice {} is linked to PO {} - Accounts Payable already booked at receipt, no GL entry posted for this invoice",
                    saved.getInvoiceNumber(), saved.getPurchaseOrder().getPoNumber());
        } else {
            postStandaloneInvoiceToGeneralLedger(saved);
        }
        return saved;
    }

    private void postStandaloneInvoiceToGeneralLedger(SupplierInvoice invoice) {
        Currency currency = invoice.getCurrency();
        Currency baseCurrency = currencyService.getBaseCurrency();
        BigDecimal exchangeRate = BigDecimal.ONE;
        if (currency != null && !currency.equals(baseCurrency)) {
            exchangeRate = currencyService.getExchangeRate(currency, baseCurrency);
        }

        FinancialEvent event = FinancialEvent.builder()
                .eventType(FinancialEventType.PURCHASE_INVOICE)
                .sourceModule(GLSourceModule.INVENTORY)
                .sourceReferenceType("SUPPLIER_INVOICE")
                .sourceReferenceId(invoice.getId())
                .idempotencyKey("SUPPLIER-INVOICE-" + invoice.getId())
                .eventDate(invoice.getInvoiceDate())
                .description("Supplier invoice " + invoice.getInvoiceNumber() + " (" + invoice.getSupplier().getName() + ")")
                .shop(invoice.getShop())
                .currency(currency)
                .exchangeRate(exchangeRate)
                .grossAmount(invoice.getTotalAmount())
                .netAmount(invoice.getSubtotalAmount())
                .taxAmount(invoice.getTaxAmount())
                .postedBy("system")
                .build();

        glPostingService.post(event);
    }

    @Transactional
    public SupplierInvoice voidInvoice(Long invoiceId, String reason) {
        SupplierInvoice invoice = findOrThrow(invoiceId);
        boolean wasPostedStandalone = invoice.getStatus() == SupplierInvoiceStatus.POSTED && !invoice.isPoLinked();

        invoice.voidInvoice(reason);
        SupplierInvoice saved = supplierInvoiceRepository.save(invoice);

        if (wasPostedStandalone) {
            journalEntryRepository.findByIdempotencyKey("SUPPLIER-INVOICE-" + invoice.getId())
                    .ifPresent(entry -> glPostingService.reverse(entry, LocalDate.now(), "Invoice voided: " + reason, "system"));
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public SupplierInvoice findOrThrow(Long invoiceId) {
        return supplierInvoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new IllegalArgumentException("Supplier invoice not found: " + invoiceId));
    }

    @Transactional(readOnly = true)
    public List<SupplierInvoice> findAll() {
        return supplierInvoiceRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<SupplierInvoice> findOutstanding() {
        return supplierInvoiceRepository.findByStatusIn(
                List.of(SupplierInvoiceStatus.POSTED, SupplierInvoiceStatus.PARTIALLY_PAID));
    }

    public SupplierInvoiceResponse toResponse(SupplierInvoice invoice) {
        return SupplierInvoiceResponse.builder()
                .id(invoice.getId())
                .invoiceNumber(invoice.getInvoiceNumber())
                .supplierId(invoice.getSupplier().getId())
                .supplierName(invoice.getSupplier().getName())
                .shopId(invoice.getShop().getId())
                .purchaseOrderId(invoice.getPurchaseOrder() != null ? invoice.getPurchaseOrder().getId() : null)
                .purchaseOrderNumber(invoice.getPurchaseOrder() != null ? invoice.getPurchaseOrder().getPoNumber() : null)
                .currencyCode(invoice.getCurrency() != null ? invoice.getCurrency().getCode() : null)
                .invoiceDate(invoice.getInvoiceDate())
                .dueDate(invoice.getDueDate())
                .subtotalAmount(invoice.getSubtotalAmount())
                .taxAmount(invoice.getTaxAmount())
                .totalAmount(invoice.getTotalAmount())
                .amountPaid(invoice.getAmountPaid())
                .outstandingAmount(invoice.getOutstandingAmount())
                .status(invoice.getStatus().name())
                .voidedReason(invoice.getVoidedReason())
                .build();
    }
}
