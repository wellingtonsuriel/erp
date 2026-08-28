package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.currency.CurrencyRepository;
import com.pos_onlineshop.hybrid.customerInvoice.CustomerInvoice;
import com.pos_onlineshop.hybrid.customerInvoice.CustomerInvoiceRepository;
import com.pos_onlineshop.hybrid.customerInvoiceLine.CustomerInvoiceLine;
import com.pos_onlineshop.hybrid.customers.Customers;
import com.pos_onlineshop.hybrid.customers.CustomersRepository;
import com.pos_onlineshop.hybrid.dtos.CreateCustomerInvoiceRequest;
import com.pos_onlineshop.hybrid.dtos.CustomerInvoiceResponse;
import com.pos_onlineshop.hybrid.enums.CustomerInvoiceStatus;
import com.pos_onlineshop.hybrid.enums.FinancialEventType;
import com.pos_onlineshop.hybrid.enums.GLSourceModule;
import com.pos_onlineshop.hybrid.gl.FinancialEvent;
import com.pos_onlineshop.hybrid.journalEntry.JournalEntryRepository;
import com.pos_onlineshop.hybrid.products.Product;
import com.pos_onlineshop.hybrid.products.ProductRepository;
import com.pos_onlineshop.hybrid.shop.Shop;
import com.pos_onlineshop.hybrid.shop.ShopRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Accounts-Receivable invoice lifecycle, mirroring SupplierInvoiceService: DRAFT -> POSTED ->
 * (PARTIALLY_PAID ->)* PAID, VOID from DRAFT or from POSTED with zero payments applied.
 *
 * Unlike SupplierInvoice, there is no order-linked case to guard against double-posting: the
 * existing POS/online Order flow always posts as an immediately-paid sale (POS_CASH_SALE /
 * POS_NON_CASH_SALE / ONLINE_ORDER_PAID), with no credit/unpaid path today. CustomerInvoice is
 * therefore a genuinely standalone credit-sale mechanism, and post() always posts
 * CUSTOMER_INVOICE to the GL.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerInvoiceService {

    private final CustomerInvoiceRepository customerInvoiceRepository;
    private final CustomersRepository customersRepository;
    private final ShopRepository shopRepository;
    private final CurrencyRepository currencyRepository;
    private final ProductRepository productRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final GLPostingService glPostingService;
    private final CurrencyService currencyService;

    @Transactional
    public CustomerInvoice createInvoice(CreateCustomerInvoiceRequest request) {
        if (customerInvoiceRepository.existsByInvoiceNumber(request.getInvoiceNumber())) {
            throw new IllegalArgumentException("An invoice with number " + request.getInvoiceNumber() + " already exists");
        }
        Customers customer = customersRepository.findById(request.getCustomerId())
                .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + request.getCustomerId()));
        Shop shop = shopRepository.findById(request.getShopId())
                .orElseThrow(() -> new IllegalArgumentException("Shop not found: " + request.getShopId()));
        Currency currency = currencyRepository.findById(request.getCurrencyId())
                .orElseThrow(() -> new IllegalArgumentException("Currency not found: " + request.getCurrencyId()));

        BigDecimal tax = request.getTaxAmount() != null ? request.getTaxAmount() : BigDecimal.ZERO;
        BigDecimal total = request.getSubtotalAmount().add(tax);

        enforceCreditLimit(customer, total);

        CustomerInvoice invoice = CustomerInvoice.builder()
                .invoiceNumber(request.getInvoiceNumber())
                .customer(customer)
                .shop(shop)
                .currency(currency)
                .invoiceDate(request.getInvoiceDate())
                .dueDate(request.getDueDate())
                .subtotalAmount(request.getSubtotalAmount())
                .taxAmount(tax)
                .totalAmount(total)
                .notes(request.getNotes())
                .build();

        if (request.getLines() != null) {
            for (CreateCustomerInvoiceRequest.Line lineRequest : request.getLines()) {
                Product product = productRepository.findById(lineRequest.getProductId())
                        .orElseThrow(() -> new IllegalArgumentException("Product not found: " + lineRequest.getProductId()));
                invoice.addLine(CustomerInvoiceLine.builder()
                        .product(product)
                        .quantity(lineRequest.getQuantity())
                        .unitPrice(lineRequest.getUnitPrice())
                        .build());
            }
        }

        CustomerInvoice saved = customerInvoiceRepository.save(invoice);
        log.info("Created customer invoice {} for {}", saved.getInvoiceNumber(), customer.getName());
        return saved;
    }

    /** Rejects the invoice if it would push the customer's total outstanding balance past their
     * credit limit. A null creditLimit means no limit is enforced. */
    private void enforceCreditLimit(Customers customer, BigDecimal newInvoiceTotal) {
        if (customer.getCreditLimit() == null) {
            return;
        }
        BigDecimal currentOutstanding = customerInvoiceRepository
                .findByCustomerAndStatusIn(customer, List.of(CustomerInvoiceStatus.POSTED, CustomerInvoiceStatus.PARTIALLY_PAID))
                .stream()
                .map(CustomerInvoice::getOutstandingAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal projected = currentOutstanding.add(newInvoiceTotal);
        if (projected.compareTo(customer.getCreditLimit()) > 0) {
            throw new IllegalArgumentException(
                    "Invoice of " + newInvoiceTotal + " would take " + customer.getName()
                            + "'s outstanding balance to " + projected + ", exceeding their credit limit of "
                            + customer.getCreditLimit());
        }
    }

    @Transactional
    public CustomerInvoice postInvoice(Long invoiceId) {
        CustomerInvoice invoice = findOrThrow(invoiceId);
        invoice.post();
        CustomerInvoice saved = customerInvoiceRepository.save(invoice);
        postToGeneralLedger(saved);
        return saved;
    }

    private void postToGeneralLedger(CustomerInvoice invoice) {
        Currency currency = invoice.getCurrency();
        Currency baseCurrency = currencyService.getBaseCurrency();
        BigDecimal exchangeRate = BigDecimal.ONE;
        if (currency != null && !currency.equals(baseCurrency)) {
            exchangeRate = currencyService.getExchangeRate(currency, baseCurrency);
        }

        FinancialEvent event = FinancialEvent.builder()
                .eventType(FinancialEventType.CUSTOMER_INVOICE)
                .sourceModule(GLSourceModule.ORDER)
                .sourceReferenceType("CUSTOMER_INVOICE")
                .sourceReferenceId(invoice.getId())
                .idempotencyKey("CUSTOMER-INVOICE-" + invoice.getId())
                .eventDate(invoice.getInvoiceDate())
                .description("Customer invoice " + invoice.getInvoiceNumber() + " (" + invoice.getCustomer().getName() + ")")
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
    public CustomerInvoice voidInvoice(Long invoiceId, String reason) {
        CustomerInvoice invoice = findOrThrow(invoiceId);
        boolean wasPosted = invoice.getStatus() == CustomerInvoiceStatus.POSTED;

        invoice.voidInvoice(reason);
        CustomerInvoice saved = customerInvoiceRepository.save(invoice);

        if (wasPosted) {
            journalEntryRepository.findByIdempotencyKey("CUSTOMER-INVOICE-" + invoice.getId())
                    .ifPresent(entry -> glPostingService.reverse(entry, LocalDate.now(), "Invoice voided: " + reason, "system"));
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public CustomerInvoice findOrThrow(Long invoiceId) {
        return customerInvoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new IllegalArgumentException("Customer invoice not found: " + invoiceId));
    }

    @Transactional(readOnly = true)
    public List<CustomerInvoice> findAll() {
        return customerInvoiceRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<CustomerInvoice> findOutstanding() {
        return customerInvoiceRepository.findByStatusIn(
                List.of(CustomerInvoiceStatus.POSTED, CustomerInvoiceStatus.PARTIALLY_PAID));
    }

    public CustomerInvoiceResponse toResponse(CustomerInvoice invoice) {
        return CustomerInvoiceResponse.builder()
                .id(invoice.getId())
                .invoiceNumber(invoice.getInvoiceNumber())
                .customerId(invoice.getCustomer().getId())
                .customerName(invoice.getCustomer().getName())
                .shopId(invoice.getShop().getId())
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
