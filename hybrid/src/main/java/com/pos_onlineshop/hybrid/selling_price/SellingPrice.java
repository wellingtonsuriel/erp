package com.pos_onlineshop.hybrid.selling_price;


import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.products.Product;
import com.pos_onlineshop.hybrid.shop.Shop;
import com.pos_onlineshop.hybrid.tax.Tax;
import com.pos_onlineshop.hybrid.enums.PriceType;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity representing selling prices for products in different shops.
 * Supports multiple pricing strategies and currencies.
 */
@Entity
@Table(name = "selling_prices",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"product_id", "shop_id", "price_type", "currency_id"})
        },
        indexes = {
                @Index(name = "idx_selling_price_product", columnList = "product_id"),
                @Index(name = "idx_selling_price_shop", columnList = "shop_id"),
                @Index(name = "idx_selling_price_active", columnList = "is_active"),
                @Index(name = "idx_selling_price_effective", columnList = "effective_from, effective_to")
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SellingPrice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_id", nullable = false)
    private Shop shop;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "currency_id", nullable = false)
    private Currency currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "price_type", nullable = false)
    private PriceType priceType;

    @Column(name = "selling_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal sellingPrice;

    @Column(name = "base_price", precision = 19, scale = 4)
    private BigDecimal basePrice;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "selling_price_taxes",
            joinColumns = @JoinColumn(name = "selling_price_id"),
            inverseJoinColumns = @JoinColumn(name = "tax_id")
    )
    @Builder.Default
    private List<Tax> taxes = new ArrayList<>();

    @Column(name = "discount_percentage", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal discountPercentage = BigDecimal.ZERO;

    @Column(name = "min_selling_price", precision = 19, scale = 4)
    private BigDecimal minSellingPrice;

    @Column(name = "max_selling_price", precision = 19, scale = 4)
    private BigDecimal maxSellingPrice;

    @Column(name = "quantity_break")
    private Integer quantityBreak; // For bulk pricing

    @Column(name = "bulk_price", precision = 19, scale = 4)
    private BigDecimal bulkPrice;

    @Column(name = "effective_from")
    @Builder.Default
    private LocalDateTime effectiveFrom = LocalDateTime.now();

    @Column(name = "effective_to")
    private LocalDateTime effectiveTo;

    @Column(name = "is_active")
    @Builder.Default
    private boolean active = true;

    @Column(name = "priority")
    @Builder.Default
    private Integer priority = 0; // Higher number = higher priority

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "notes")
    private String notes;

    @PrePersist
    public void prePersist() {
        calculateSellingPriceFromBaseAndTaxes();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
        calculateSellingPriceFromBaseAndTaxes();
    }

    /**
     * Calculate selling price from base price and associated taxes.
     * Supports three tax calculation types:
     * - FIXED: Flat tax amount added to base price
     * - PERCENTAGE: Percentage of base price (e.g., 15% VAT)
     * - TIERED: Progressive tax brackets (requires tier configuration in Tax entity)
     */
    private void calculateSellingPriceFromBaseAndTaxes() {
        // Validate inputs
        if (basePrice == null || taxes == null || taxes.isEmpty()) {
            return;
        }

        BigDecimal totalTaxAmount = BigDecimal.ZERO;

        // Calculate total tax amount from all associated taxes
        // Each tax's calculation method (FIXED/PERCENTAGE/TIERED) is handled by Tax.calculateTaxAmount()
        for (Tax tax : taxes) {
            if (tax != null && tax.getActive()) {
                BigDecimal individualTaxAmount = tax.calculateTaxAmount(basePrice);
                totalTaxAmount = totalTaxAmount.add(individualTaxAmount);
            }
        }

        // Set selling price as base price + total tax amount
        this.sellingPrice = basePrice.add(totalTaxAmount).setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * Check if this price is currently effective
     */
    public boolean isCurrentlyEffective() {
        LocalDateTime now = LocalDateTime.now();
        return active &&
                (effectiveFrom == null || !effectiveFrom.isAfter(now)) &&
                (effectiveTo == null || !effectiveTo.isBefore(now));
    }

    /**
     * Calculate the final price after discount
     */
    public BigDecimal getFinalPrice() {
        if (discountPercentage == null || discountPercentage.compareTo(BigDecimal.ZERO) == 0) {
            return sellingPrice;
        }

        BigDecimal discountAmount = sellingPrice
                .multiply(discountPercentage)
                .divide(BigDecimal.valueOf(100));

        return sellingPrice.subtract(discountAmount);
    }

    /**
     * Get the applicable price based on quantity
     */
    public BigDecimal getApplicablePrice(Integer quantity) {
        if (quantity != null && quantityBreak != null &&
                quantity >= quantityBreak && bulkPrice != null) {
            return bulkPrice;
        }
        return getFinalPrice();
    }
}

