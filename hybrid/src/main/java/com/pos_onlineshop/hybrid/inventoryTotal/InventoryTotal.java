package com.pos_onlineshop.hybrid.inventoryTotal;

import com.pos_onlineshop.hybrid.products.Product;
import com.pos_onlineshop.hybrid.shop.Shop;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * InventoryTotal entity tracking the running total stock per product per shop - the single
 * authoritative operational stock balance for both POS and online sales (see
 * ShopInventoryService's class comment). ShopInventory remains an immutable receipt/lot
 * history used only for cost lookup, never as a current-quantity source.
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

    /** Stock committed to a not-yet-finalized order (currently: a PENDING online order) but
     * not yet physically removed from the shelf. Always &lt;= totalstock. */
    @Column(name = "reserved_stock", nullable = false, columnDefinition = "INT NOT NULL DEFAULT 0")
    @Builder.Default
    private Integer reservedStock = 0;

    @Column(name = "last_updated")
    @Builder.Default
    private LocalDateTime lastUpdated = LocalDateTime.now();

    @Version
    private Long version; // Optimistic locking for concurrent updates

    /** totalstock minus whatever is already committed to a pending order. */
    public int getAvailableStock() {
        int total = totalstock != null ? totalstock : 0;
        int reserved = reservedStock != null ? reservedStock : 0;
        return total - reserved;
    }
}
