package com.pos_onlineshop.hybrid.inventory;

import com.pos_onlineshop.hybrid.products.Product;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "inventory_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "product_id", nullable = false, unique = true)
    private Product product;

    @Column(nullable = false)
    @Builder.Default
    private Integer quantity = 0;

    @Column(name = "reserved_quantity")
    @Builder.Default
    private Integer reservedQuantity = 0; // For online orders

    @Column(name = "reorder_level")
    @Builder.Default
    private Integer reorderLevel = 10;

    @Version
    private Long version; // Optimistic locking

    // Business methods
    public Integer getAvailableQuantity() {
        return quantity - reservedQuantity;
    }

    public void decrementQuantity(int amount) {
        if (this.getAvailableQuantity() < amount) {
            throw new IllegalArgumentException("Insufficient inventory");
        }
        this.quantity -= amount;
    }

    public void incrementQuantity(int amount) {
        this.quantity += amount;
    }

    public boolean needsReorder() {
        return this.quantity <= this.reorderLevel;
    }
}