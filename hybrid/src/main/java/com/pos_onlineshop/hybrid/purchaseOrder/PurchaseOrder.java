package com.pos_onlineshop.hybrid.purchaseOrder;

import com.pos_onlineshop.hybrid.cashier.Cashier;
import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.enums.PurchaseOrderStatus;
import com.pos_onlineshop.hybrid.purchaseOrderLine.PurchaseOrderLine;
import com.pos_onlineshop.hybrid.shop.Shop;
import com.pos_onlineshop.hybrid.suppliers.Suppliers;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * DRAFT -> SUBMITTED -> APPROVED -> (PARTIALLY_RECEIVED ->)* RECEIVED -> CLOSED,
 * with CANCELLED reachable from any non-terminal state. Receiving reuses
 * ShopInventoryService.createShopInventory line by line (see PurchaseOrderService) rather
 * than duplicating stock/GL logic - each receipt is a normal ShopInventory lot, already
 * wired to post STOCK_RECEIPT to the GL.
 */
@Entity
@Table(name = "purchase_orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"lines", "supplier", "shop", "currency", "createdBy", "approvedBy"})
@ToString(exclude = {"lines", "supplier", "shop", "currency", "createdBy", "approvedBy"})
public class PurchaseOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "po_number", nullable = false, unique = true, length = 40)
    private String poNumber;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Suppliers supplier;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "shop_id", nullable = false)
    private Shop shop;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "currency_id", nullable = false)
    private Currency currency;

    @Column(name = "order_date", nullable = false)
    @Builder.Default
    private LocalDate orderDate = LocalDate.now();

    @Column(name = "expected_delivery_date")
    private LocalDate expectedDeliveryDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PurchaseOrderStatus status = PurchaseOrderStatus.DRAFT;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "created_by")
    private Cashier createdBy;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "approved_by")
    private Cashier approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Column(length = 1000)
    private String notes;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<PurchaseOrderLine> lines = new ArrayList<>();

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void addLine(PurchaseOrderLine line) {
        lines.add(line);
        line.setPurchaseOrder(this);
    }

    public BigDecimal getTotalValue() {
        return lines.stream().map(PurchaseOrderLine::getLineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public boolean canBeSubmitted() {
        return status == PurchaseOrderStatus.DRAFT && !lines.isEmpty();
    }

    public boolean canBeApproved() {
        return status == PurchaseOrderStatus.SUBMITTED;
    }

    public boolean canBeReceived() {
        return status == PurchaseOrderStatus.APPROVED || status == PurchaseOrderStatus.PARTIALLY_RECEIVED;
    }

    public boolean canBeCancelled() {
        return status != PurchaseOrderStatus.RECEIVED
                && status != PurchaseOrderStatus.CLOSED
                && status != PurchaseOrderStatus.CANCELLED;
    }

    public boolean canBeClosed() {
        return status == PurchaseOrderStatus.RECEIVED;
    }

    public void submit() {
        if (!canBeSubmitted()) {
            throw new IllegalStateException("Purchase order " + poNumber + " cannot be submitted from status " + status);
        }
        this.status = PurchaseOrderStatus.SUBMITTED;
    }

    public void approve(Cashier approver) {
        if (!canBeApproved()) {
            throw new IllegalStateException("Purchase order " + poNumber + " cannot be approved from status " + status);
        }
        this.status = PurchaseOrderStatus.APPROVED;
        this.approvedBy = approver;
        this.approvedAt = LocalDateTime.now();
    }

    public void cancel(String reason) {
        if (!canBeCancelled()) {
            throw new IllegalStateException("Purchase order " + poNumber + " cannot be cancelled from status " + status);
        }
        this.status = PurchaseOrderStatus.CANCELLED;
        this.cancelledAt = LocalDateTime.now();
        this.cancellationReason = reason;
    }

    public void close() {
        if (!canBeClosed()) {
            throw new IllegalStateException("Purchase order " + poNumber + " cannot be closed from status " + status);
        }
        this.status = PurchaseOrderStatus.CLOSED;
    }

    /** Call after applying receipts to one or more lines to move the header to the right status. */
    public void refreshStatusAfterReceipt() {
        if (!canBeReceived()) {
            return;
        }
        boolean allFullyReceived = lines.stream().allMatch(PurchaseOrderLine::isFullyReceived);
        boolean anyReceived = lines.stream().anyMatch(l -> l.getQuantityReceived() > 0);
        if (allFullyReceived) {
            this.status = PurchaseOrderStatus.RECEIVED;
        } else if (anyReceived) {
            this.status = PurchaseOrderStatus.PARTIALLY_RECEIVED;
        }
    }
}
