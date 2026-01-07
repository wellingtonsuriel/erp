package com.pos_onlineshop.hybrid.productPrice;

import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.enums.PriceType;
import com.pos_onlineshop.hybrid.products.Product;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "product_prices",
        uniqueConstraints = @UniqueConstraint(columnNames = {"product_id", "currency_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"product"})
@ToString(exclude = {"product"})
public class ProductPrice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "currency_id", nullable = false)
    private Currency currency;

    @Column(name = "price", nullable = false, precision = 19, scale = 4)
    private BigDecimal price;

    @Column(name = "effective_date")
    @Builder.Default
    private LocalDateTime effectiveDate = LocalDateTime.now();

    @Column(name = "expiry_date")
    private LocalDateTime expiryDate;

    @Column(name = "is_active")
    @Builder.Default
    private boolean active = true;

    @Column(name = "price_type")
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private PriceType priceType = PriceType.REGULAR;

    @Column(name = "min_quantity", precision = 10, scale = 3)
    @Builder.Default
    private BigDecimal minQuantity = BigDecimal.ONE;

    @Column(name = "max_quantity", precision = 10, scale = 3)
    private BigDecimal maxQuantity;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Version
    private Long version;

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // Business methods

    /**
     * Check if this price is currently effective
     */
    public boolean isEffective() {
        LocalDateTime now = LocalDateTime.now();

        if (effectiveDate != null && now.isBefore(effectiveDate)) {
            return false;
        }

        return expiryDate == null || now.isBefore(expiryDate);
    }

    /**
     * Get effective price (same as getPrice for now, but could include promotions)
     */
    public BigDecimal getEffectivePrice() {
        return price;
    }

    /**
     * Check if price is valid for given quantity
     */
    public boolean isValidForQuantity(BigDecimal quantity) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }

        if (minQuantity != null && quantity.compareTo(minQuantity) < 0) {
            return false;
        }

        return maxQuantity == null || quantity.compareTo(maxQuantity) <= 0;
    }

    /**
     * Check if this is a promotional price
     */
    public boolean isPromotional() {
        return priceType == PriceType.PROMOTIONAL || priceType == PriceType.SALE;
    }

    /**
     * Check if price has expired
     */
    public boolean hasExpired() {
        return expiryDate != null && LocalDateTime.now().isAfter(expiryDate);
    }

    /**
     * Activate this price
     */
    public void activate() {
        this.active = true;
        if (this.effectiveDate == null || this.effectiveDate.isAfter(LocalDateTime.now())) {
            this.effectiveDate = LocalDateTime.now();
        }
    }

    /**
     * Deactivate this price
     */
    public void deactivate() {
        this.active = false;
    }

    /**
     * Set expiry date and deactivate if already expired
     */
    public void setExpiryDate(LocalDateTime expiryDate) {
        this.expiryDate = expiryDate;
        if (hasExpired()) {
            deactivate();
        }
    }


}