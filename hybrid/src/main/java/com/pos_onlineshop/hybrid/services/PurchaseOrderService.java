package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.cashier.Cashier;
import com.pos_onlineshop.hybrid.cashier.CashierRepository;
import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.currency.CurrencyRepository;
import com.pos_onlineshop.hybrid.dtos.CreatePurchaseOrderRequest;
import com.pos_onlineshop.hybrid.dtos.CreateShopInventoryRequest;
import com.pos_onlineshop.hybrid.dtos.PurchaseOrderResponse;
import com.pos_onlineshop.hybrid.dtos.ReceivePurchaseOrderRequest;
import com.pos_onlineshop.hybrid.products.Product;
import com.pos_onlineshop.hybrid.products.ProductRepository;
import com.pos_onlineshop.hybrid.purchaseOrder.PurchaseOrder;
import com.pos_onlineshop.hybrid.purchaseOrder.PurchaseOrderRepository;
import com.pos_onlineshop.hybrid.purchaseOrderLine.PurchaseOrderLine;
import com.pos_onlineshop.hybrid.shop.Shop;
import com.pos_onlineshop.hybrid.shop.ShopRepository;
import com.pos_onlineshop.hybrid.suppliers.Suppliers;
import com.pos_onlineshop.hybrid.suppliers.SuppliersRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Purchase-order workflow: DRAFT -> SUBMITTED -> APPROVED -> (PARTIALLY_RECEIVED ->)* RECEIVED
 * -> CLOSED, CANCELLED reachable from any non-terminal state.
 *
 * Receiving deliberately does NOT construct ShopInventory rows or GL entries itself - it calls
 * ShopInventoryService.createShopInventory once per received line, the same entry point the
 * manual "add stock" admin flow uses. That method already posts STOCK_RECEIPT to the GL (see
 * the earlier commit), so a PO receipt gets correct, tested GL posting for free instead of a
 * second, parallel implementation of the same accounting.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PurchaseOrderService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final SuppliersRepository suppliersRepository;
    private final ShopRepository shopRepository;
    private final CurrencyRepository currencyRepository;
    private final ProductRepository productRepository;
    private final CashierRepository cashierRepository;
    private final ShopInventoryService shopInventoryService;

    @Transactional
    public PurchaseOrder createPurchaseOrder(CreatePurchaseOrderRequest request) {
        Suppliers supplier = suppliersRepository.findById(request.getSupplierId())
                .orElseThrow(() -> new IllegalArgumentException("Supplier not found: " + request.getSupplierId()));
        Shop shop = shopRepository.findById(request.getShopId())
                .orElseThrow(() -> new IllegalArgumentException("Shop not found: " + request.getShopId()));
        Currency currency = currencyRepository.findById(request.getCurrencyId())
                .orElseThrow(() -> new IllegalArgumentException("Currency not found: " + request.getCurrencyId()));

        Cashier createdBy = null;
        if (request.getCreatedById() != null) {
            createdBy = cashierRepository.findById(request.getCreatedById())
                    .orElseThrow(() -> new IllegalArgumentException("Cashier not found: " + request.getCreatedById()));
        }

        PurchaseOrder po = PurchaseOrder.builder()
                .poNumber(generatePoNumber())
                .supplier(supplier)
                .shop(shop)
                .currency(currency)
                .expectedDeliveryDate(request.getExpectedDeliveryDate())
                .notes(request.getNotes())
                .createdBy(createdBy)
                .build();

        for (CreatePurchaseOrderRequest.Line lineRequest : request.getLines()) {
            Product product = productRepository.findById(lineRequest.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + lineRequest.getProductId()));

            po.addLine(PurchaseOrderLine.builder()
                    .product(product)
                    .quantityOrdered(lineRequest.getQuantity())
                    .unitCost(lineRequest.getUnitCost())
                    .build());
        }

        PurchaseOrder saved = purchaseOrderRepository.save(po);
        log.info("Created purchase order {} for supplier {} at shop {}", saved.getPoNumber(), supplier.getName(), shop.getName());
        return saved;
    }

    @Transactional
    public PurchaseOrder submit(Long poId) {
        PurchaseOrder po = findOrThrow(poId);
        po.submit();
        return purchaseOrderRepository.save(po);
    }

    @Transactional
    public PurchaseOrder approve(Long poId, Long approverId) {
        PurchaseOrder po = findOrThrow(poId);
        Cashier approver = cashierRepository.findById(approverId)
                .orElseThrow(() -> new IllegalArgumentException("Cashier not found: " + approverId));
        po.approve(approver);
        PurchaseOrder saved = purchaseOrderRepository.save(po);
        log.info("Approved purchase order {} by {}", saved.getPoNumber(), approver.getFullName());
        return saved;
    }

    @Transactional
    public PurchaseOrder cancel(Long poId, String reason) {
        PurchaseOrder po = findOrThrow(poId);
        po.cancel(reason);
        return purchaseOrderRepository.save(po);
    }

    @Transactional
    public PurchaseOrder close(Long poId) {
        PurchaseOrder po = findOrThrow(poId);
        po.close();
        return purchaseOrderRepository.save(po);
    }

    /**
     * Receives one or more lines against an APPROVED or PARTIALLY_RECEIVED purchase order.
     * Each line is received as its own ShopInventory lot (supplier/cost/currency all carried
     * over from the PO), which independently posts its own STOCK_RECEIPT journal entry -
     * receiving 3 products in one call produces 3 lots and 3 journal entries, not one combined
     * entry, which keeps each GL entry traceable to exactly one PurchaseOrderLine.
     */
    @Transactional
    public PurchaseOrder receive(Long poId, ReceivePurchaseOrderRequest request) {
        PurchaseOrder po = findOrThrow(poId);
        if (!po.canBeReceived()) {
            throw new IllegalStateException(
                    "Purchase order " + po.getPoNumber() + " cannot be received from status " + po.getStatus());
        }

        for (ReceivePurchaseOrderRequest.Line receiveLine : request.getLines()) {
            PurchaseOrderLine line = po.getLines().stream()
                    .filter(l -> l.getProduct().getId().equals(receiveLine.getProductId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Product " + receiveLine.getProductId() + " is not on purchase order " + po.getPoNumber()));

            // Throws IllegalArgumentException if this would over-receive the line.
            line.applyReceipt(receiveLine.getReceivedQuantity());

            CreateShopInventoryRequest stockRequest = new CreateShopInventoryRequest();
            stockRequest.setShopId(po.getShop().getId());
            stockRequest.setProductId(line.getProduct().getId());
            stockRequest.setSupplierId(po.getSupplier().getId());
            stockRequest.setCurrencyId(po.getCurrency().getId());
            stockRequest.setQuantity(receiveLine.getReceivedQuantity());
            stockRequest.setUnitPrice(line.getUnitCost());

            shopInventoryService.createShopInventory(stockRequest);
        }

        po.refreshStatusAfterReceipt();
        PurchaseOrder saved = purchaseOrderRepository.save(po);
        log.info("Received against purchase order {} - status now {}", saved.getPoNumber(), saved.getStatus());
        return saved;
    }

    @Transactional(readOnly = true)
    public PurchaseOrder findOrThrow(Long poId) {
        return purchaseOrderRepository.findById(poId)
                .orElseThrow(() -> new IllegalArgumentException("Purchase order not found: " + poId));
    }

    @Transactional(readOnly = true)
    public List<PurchaseOrder> findAll() {
        return purchaseOrderRepository.findAll();
    }

    public PurchaseOrderResponse toResponse(PurchaseOrder po) {
        return PurchaseOrderResponse.builder()
                .id(po.getId())
                .poNumber(po.getPoNumber())
                .supplierId(po.getSupplier().getId())
                .supplierName(po.getSupplier().getName())
                .shopId(po.getShop().getId())
                .shopName(po.getShop().getName())
                .currencyCode(po.getCurrency() != null ? po.getCurrency().getCode() : null)
                .status(po.getStatus().name())
                .orderDate(po.getOrderDate())
                .expectedDeliveryDate(po.getExpectedDeliveryDate())
                .createdByName(po.getCreatedBy() != null ? po.getCreatedBy().getFullName() : null)
                .approvedByName(po.getApprovedBy() != null ? po.getApprovedBy().getFullName() : null)
                .approvedAt(po.getApprovedAt())
                .cancellationReason(po.getCancellationReason())
                .totalValue(po.getTotalValue())
                .lines(po.getLines().stream().map(line -> PurchaseOrderResponse.LineResponse.builder()
                        .productId(line.getProduct().getId())
                        .productName(line.getProduct().getName())
                        .quantityOrdered(line.getQuantityOrdered())
                        .quantityReceived(line.getQuantityReceived())
                        .outstandingQuantity(line.getOutstandingQuantity())
                        .unitCost(line.getUnitCost())
                        .lineTotal(line.getLineTotal())
                        .build()).collect(Collectors.toList()))
                .build();
    }

    private String generatePoNumber() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        return "PO-" + timestamp;
    }
}
