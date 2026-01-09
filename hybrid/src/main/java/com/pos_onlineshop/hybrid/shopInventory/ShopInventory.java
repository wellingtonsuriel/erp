package com.pos_onlineshop.hybrid.shopInventory;

import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.products.Product;
import com.pos_onlineshop.hybrid.shop.Shop;
import com.pos_onlineshop.hybrid.suppliers.Suppliers;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * ShopInventory entity tracking product stock levels per shop.
 * Enhanced with better stock management features.
 */
@Entity
@Table(name = "shop_inventories",
        uniqueConstraints = @UniqueConstraint(columnNames = {"shop_id", "product_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"shop", "product"})
@ToString(exclude = {"shop", "product"})
public class ShopInventory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_id", nullable = false)
    private Shop shop;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Suppliers suppliers;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "expiry_date")
    private LocalDateTime expiryDate;



    @Column(nullable = false)
    @Builder.Default
    private Integer quantity = 0;



    @Column(name = "in_transit_quantity", nullable = false)
    @Builder.Default
    private Integer inTransitQuantity = 0;

    /**
     * Total cumulative stock added to this inventory (lifetime tracking)
     * This field only increases with addStock operations and provides audit trail
     */


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "currency_id", nullable = false)
    private Currency currency;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitPrice;


    @Column(name = "reorder_level")
    private Integer reorderLevel;

    @Column(name = "min_stock")
    private Integer minStock;

    @Column(name = "max_stock")
    private Integer maxStock;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime addedAt = LocalDateTime.now();





}
