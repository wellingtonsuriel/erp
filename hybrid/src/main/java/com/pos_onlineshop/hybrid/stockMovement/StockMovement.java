package com.pos_onlineshop.hybrid.stockMovement;

import com.pos_onlineshop.hybrid.products.Product;
import com.pos_onlineshop.hybrid.shop.Shop;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * StockMovement entity for tracking all stock additions and reductions (audit trail).
 * Each stock operation creates a new record in this table.
 */
@Entity
@Table(name = "stock_movements",
        indexes = {
                @Index(name = "idx_shop_product", columnList = "shop_id,product_id"),
                @Index(name = "idx_created_at", columnList = "created_at")
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"shop", "product"})
@ToString(exclude = {"shop", "product"})
public class StockMovement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_id", nullable = false)
    private Shop shop;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MovementType movementType;

    @Column(length = 500)
    private String notes;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "created_by")
    private String createdBy;

    public enum MovementType {
        ADDITION,    // Stock added
        REDUCTION,   // Stock reduced (sale, transfer, damage, etc.)
        ADJUSTMENT   // Manual adjustment
    }
}
