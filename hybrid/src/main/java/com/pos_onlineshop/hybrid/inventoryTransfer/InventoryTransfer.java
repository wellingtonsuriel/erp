package com.pos_onlineshop.hybrid.inventoryTransfer;

import com.pos_onlineshop.hybrid.cashier.Cashier;
import com.pos_onlineshop.hybrid.damagedStockReceived.DamagedStockReceived;
import com.pos_onlineshop.hybrid.enums.TransferPriority;
import com.pos_onlineshop.hybrid.enums.TransferStatus;
import com.pos_onlineshop.hybrid.enums.TransferType;
import com.pos_onlineshop.hybrid.inventoryTransferItems.InventoryTransferItem;
import com.pos_onlineshop.hybrid.shop.Shop;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * InventoryTransfer entity tracking inventory movements between shops.
 * Enhanced with multi-item support and better tracking.
 */
@Entity
@Table(name = "inventory_transfers")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"transferItems", "damagedItems"})
@ToString(exclude = {"transferItems", "damagedItems"})
public class InventoryTransfer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_shop_id", nullable = false)
    private Shop fromShop;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_shop_id", nullable = false)
    private Shop toShop;

    @OneToMany(mappedBy = "transfer", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<InventoryTransferItem> transferItems = new ArrayList<>();

    @OneToMany(mappedBy = "transfer", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<DamagedStockReceived> damagedItems = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private TransferStatus status = TransferStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private TransferType transferType = TransferType.REPLENISHMENT;

    @Column(name = "transfer_number", unique = true, nullable = false)
    private String transferNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "initiated_by", nullable = false)
    private Cashier initiatedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private Cashier approvedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipped_by")
    private Cashier shippedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "received_by")
    private Cashier receivedBy;

    @Column(length = 1000)
    private String notes;

    @Column(name = "priority")
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private TransferPriority priority = TransferPriority.NORMAL;

    @Column(name = "total_value", precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal totalValue = BigDecimal.ZERO;

    @Column(name = "requested_at", nullable = false)
    @Builder.Default
    private LocalDateTime requestedAt = LocalDateTime.now();

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "shipped_at")
    private LocalDateTime shippedAt;

    @Column(name = "expected_delivery")
    private LocalDateTime expectedDelivery;

    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // Business methods
    public void addTransferItem(InventoryTransferItem item) {
        transferItems.add(item);
        item.setTransfer(this);
        recalculateTotal();
    }

    public void removeTransferItem(InventoryTransferItem item) {
        transferItems.remove(item);
        item.setTransfer(null);
        recalculateTotal();
    }

    public int getTotalItems() {
        return transferItems.stream()
                .mapToInt(InventoryTransferItem::getRequestedQuantity)
                .sum();
    }

    public int getTotalShippedItems() {
        return transferItems.stream()
                .mapToInt(item -> item.getShippedQuantity() != null ? item.getShippedQuantity() : 0)
                .sum();
    }

    public int getTotalReceivedItems() {
        return transferItems.stream()
                .mapToInt(item -> item.getReceivedQuantity() != null ? item.getReceivedQuantity() : 0)
                .sum();
    }

    public int getTotalDamagedItems() {
        return transferItems.stream()
                .mapToInt(item -> item.getDamagedQuantity() != null ? item.getDamagedQuantity() : 0)
                .sum();
    }

    public BigDecimal getTotalReceivedValue() {
        return transferItems.stream()
                .map(InventoryTransferItem::getReceivedValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getTotalDamageValue() {
        return transferItems.stream()
                .map(InventoryTransferItem::getDamageValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public boolean isFullyShipped() {
        return transferItems.stream().allMatch(InventoryTransferItem::isFullyShipped);
    }

    public boolean isFullyReceived() {
        return transferItems.stream().allMatch(InventoryTransferItem::isFullyReceived);
    }

    public boolean hasDiscrepancies() {
        return transferItems.stream().anyMatch(InventoryTransferItem::hasDiscrepancy);
    }

    public boolean canBeApproved() {
        return status == TransferStatus.PENDING;
    }

    public boolean canBeShipped() {
        return status == TransferStatus.APPROVED;
    }

    public boolean canBeReceived() {
        return status == TransferStatus.IN_TRANSIT;
    }

    public boolean canBeCancelled() {
        return status != TransferStatus.RECEIVED &&
                status != TransferStatus.CANCELLED &&
                status != TransferStatus.COMPLETED;
    }

    public boolean isOverdue() {
        return expectedDelivery != null &&
                expectedDelivery.isBefore(LocalDateTime.now()) &&
                status == TransferStatus.IN_TRANSIT;
    }

    public void approve(Cashier approver) {
        if (!canBeApproved()) {
            throw new IllegalStateException("Transfer cannot be approved in current status: " + status);
        }
        this.status = TransferStatus.APPROVED;
        this.approvedBy = approver;
        this.approvedAt = LocalDateTime.now();
    }

    public void ship(Cashier shipper) {
        if (!canBeShipped()) {
            throw new IllegalStateException("Transfer cannot be shipped in current status: " + status);
        }
        this.status = TransferStatus.IN_TRANSIT;
        this.shippedBy = shipper;
        this.shippedAt = LocalDateTime.now();
    }

    public void receive(Cashier receiver) {
        if (!canBeReceived()) {
            throw new IllegalStateException("Transfer cannot be received in current status: " + status);
        }
        this.status = TransferStatus.RECEIVED;
        this.receivedBy = receiver;
        this.receivedAt = LocalDateTime.now();

        // If fully received without discrepancies, mark as completed
        if (isFullyReceived() && !hasDiscrepancies()) {
            this.status = TransferStatus.COMPLETED;
        }
    }

    public void cancel(String reason) {
        if (!canBeCancelled()) {
            throw new IllegalStateException("Transfer cannot be cancelled in current status: " + status);
        }
        this.status = TransferStatus.CANCELLED;
        this.cancelledAt = LocalDateTime.now();
        this.cancellationReason = reason;
    }

    public void complete() {
        if (status != TransferStatus.RECEIVED) {
            throw new IllegalStateException("Transfer must be received before completion");
        }
        this.status = TransferStatus.COMPLETED;
    }

    private void recalculateTotal() {
        this.totalValue = transferItems.stream()
                .map(InventoryTransferItem::getTotalCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // Helper method to get transfer duration
    public long getTransferDurationInHours() {
        if (shippedAt == null || receivedAt == null) {
            return 0;
        }
        return java.time.Duration.between(shippedAt, receivedAt).toHours();
    }

    // Helper method to check if transfer is urgent
    public boolean isUrgent() {
        return priority == TransferPriority.URGENT || priority == TransferPriority.CRITICAL;
    }

    // Damaged items management methods
    public void addDamagedItem(DamagedStockReceived damagedItem) {
        damagedItems.add(damagedItem);
        damagedItem.setTransfer(this);
    }

    public void removeDamagedItem(DamagedStockReceived damagedItem) {
        damagedItems.remove(damagedItem);
        damagedItem.setTransfer(null);
    }

    public int getTotalDamagedItemsCount() {
        return damagedItems.stream()
                .mapToInt(DamagedStockReceived::getDamagedQuantity)
                .sum();
    }

    public BigDecimal getTotalDamagedStockValue() {
        return damagedItems.stream()
                .map(DamagedStockReceived::getTotalDamageValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public boolean hasDamagedItems() {
        return !damagedItems.isEmpty();
    }

    public long getDamagedItemsWithInsuranceClaims() {
        return damagedItems.stream()
                .filter(DamagedStockReceived::getInsuranceClaimed)
                .count();
    }

    public long getRepairableDamagedItemsCount() {
        return damagedItems.stream()
                .filter(DamagedStockReceived::getRepairable)
                .count();
    }
}