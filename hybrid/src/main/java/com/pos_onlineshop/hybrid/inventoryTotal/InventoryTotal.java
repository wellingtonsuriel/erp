package com.pos_onlineshop.hybrid.inventoryTotal;

import com.pos_onlineshop.hybrid.products.Product;
import com.pos_onlineshop.hybrid.shop.Shop;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * InventoryTotal entity tracking the running total stock per product per shop.
 * This table is updated whenever stock is added or reduced.
 */
@Entity
@Table(name = "inventory_total",
        uniqueConstraints = @UniqueConstraint(columnNames = {"shop_id", "product_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"shop", "product"})
@ToString(exclude = {"shop", "product"})
public class InventoryTotal {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_id", nullable = false)
    private Shop shop;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "totalstock", nullable = false)
    @Builder.Default
    private Integer totalstock = 0;

    @Column(name = "last_updated")
    @Builder.Default
    private LocalDateTime lastUpdated = LocalDateTime.now();

    @Version
    private Long version; // Optimistic locking for concurrent updates
}
