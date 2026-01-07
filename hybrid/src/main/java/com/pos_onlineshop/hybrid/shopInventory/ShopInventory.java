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

    @Column(unique = true)
    private String barcode;

    @Column(name = "sku", unique = true, nullable = false)
    private String sku;

    @Column(nullable = false)
    @Builder.Default
    private Integer quantity = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "currency_id", nullable = false)
    private Currency currency;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitPrice;


    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime addedAt = LocalDateTime.now();





}
