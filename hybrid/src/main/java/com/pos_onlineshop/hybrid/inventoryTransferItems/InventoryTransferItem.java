package com.pos_onlineshop.hybrid.inventoryTransferItems;



import com.pos_onlineshop.hybrid.inventoryTransfer.InventoryTransfer;
import com.pos_onlineshop.hybrid.products.Product;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * InventoryTransferItem entity representing individual items in a transfer.
 * Tracks quantities requested, shipped, and received for each product.
 */
@Entity
@Table(name = "inventory_transfer_items",
        uniqueConstraints = @UniqueConstraint(columnNames = {"transfer_id", "product_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"transfer"})
@ToString(exclude = {"transfer"})
public class InventoryTransferItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transfer_id", nullable = false)
    private InventoryTransfer transfer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "requested_quantity", nullable = false)
    private Integer requestedQuantity;

    @Column(name = "shipped_quantity")
    @Builder.Default
    private Integer shippedQuantity = 0;

    @Column(name = "received_quantity")
    @Builder.Default
    private Integer receivedQuantity = 0;

    @Column(name = "damaged_quantity")
    @Builder.Default
    private Integer damagedQuantity = 0;

    @Column(length = 500)
    private String notes;

    @Column(name = "unit_cost", precision = 19, scale = 2)
    private java.math.BigDecimal unitCost;

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
    public Integer getQuantity() {
        return requestedQuantity;
    }

    public void setQuantity(Integer quantity) {
        this.requestedQuantity = quantity;
    }

    public Integer getPendingQuantity() {
        return requestedQuantity - shippedQuantity;
    }

    public Integer getOutstandingQuantity() {
        return shippedQuantity - receivedQuantity;
    }

    public boolean isFullyShipped() {
        return shippedQuantity != null && shippedQuantity.equals(requestedQuantity);
    }

    public boolean isFullyReceived() {
        return receivedQuantity != null && receivedQuantity.equals(shippedQuantity);
    }

    public boolean hasDiscrepancy() {
        return receivedQuantity != null && !receivedQuantity.equals(shippedQuantity);
    }

    public BigDecimal getTotalCost() {
        if (unitCost == null || requestedQuantity == null) {
            return java.math.BigDecimal.ZERO;
        }
        return unitCost.multiply(java.math.BigDecimal.valueOf(requestedQuantity));
    }

    public BigDecimal getReceivedValue() {
        if (unitCost == null || receivedQuantity == null) {
            return java.math.BigDecimal.ZERO;
        }
        return unitCost.multiply(java.math.BigDecimal.valueOf(receivedQuantity));
    }

    public BigDecimal getDamageValue() {
        if (unitCost == null || damagedQuantity == null) {
            return java.math.BigDecimal.ZERO;
        }
        return unitCost.multiply(java.math.BigDecimal.valueOf(damagedQuantity));
    }

    public void shipQuantity(Integer quantity) {
        if (quantity > getPendingQuantity()) {
            throw new IllegalArgumentException("Cannot ship more than pending quantity");
        }
        this.shippedQuantity = (this.shippedQuantity == null ? 0 : this.shippedQuantity) + quantity;
    }

    public void receiveQuantity(Integer quantity, Integer damaged) {
        if (quantity + (damaged != null ? damaged : 0) > getOutstandingQuantity()) {
            throw new IllegalArgumentException("Cannot receive more than shipped quantity");
        }
        this.receivedQuantity = (this.receivedQuantity == null ? 0 : this.receivedQuantity) + quantity;
        this.damagedQuantity = (this.damagedQuantity == null ? 0 : this.damagedQuantity) + (damaged != null ? damaged : 0);
    }
}