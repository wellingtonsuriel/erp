package com.pos_onlineshop.hybrid.products;

import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.productPrice.ProductPrice;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Entity
@Table(name = "products")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"productPrices"})
@ToString(exclude = {"productPrices"})
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false)
    private String category;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "is_weighable")
    @Builder.Default
    private boolean weighable = false;

    @Column(name = "is_active")
    @Builder.Default
    private boolean active = true;


    @Column(name = "max_stock")
    private Integer maxStock; // Maximum stock this shop can hold for this product

    @Column(name = "min_stock")
    @Builder.Default
    private Integer minStock = 0; // Minimum stock to maintain

    @Column(name = "weight", precision = 10, scale = 3)
    private BigDecimal weight; // For weighable products

    @Column(name = "unit_of_measure")
    private String unitOfMeasure; // kg, piece, liter, etc.


    @Column(name = "actual_measure")
    private String actualMeasure; // kg, piece, liter, etc.


    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Version
    private Long version; // For optimistic locking

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // Business methods

    /**
     * Get product price in specific currency
     */
    public BigDecimal getPriceInCurrency(Currency currency) {
        if (currency == null) {
            throw new IllegalArgumentException("Currency cannot be null");
        }

        // If requesting base currency, return base price
        if (currency.equals(baseCurrency)) {
            return price;
        }

        // Look for specific currency price
        Optional<ProductPrice> priceOpt = productPrices.stream()
                .filter(pp -> pp.getCurrency().equals(currency) && pp.isActive() && pp.isEffective())
                .findFirst();

        return priceOpt.map(ProductPrice::getPrice).orElse(null);
    }

    /**
     * Get default currency for this product
     */
    public Currency getDefaultCurrency() {
        return baseCurrency;
    }

    /**
     * Add or update price for specific currency
     */
    public void addPrice(Currency currency, BigDecimal priceAmount) {
        if (currency == null || priceAmount == null) {
            throw new IllegalArgumentException("Currency and price cannot be null");
        }

        // Remove existing price for this currency
        productPrices.removeIf(pp -> pp.getCurrency().equals(currency));

        // Add new price
        ProductPrice productPrice = ProductPrice.builder()
                .product(this)
                .currency(currency)
                .price(priceAmount)
                .active(true)
                .effectiveDate(LocalDateTime.now())
                .build();

        productPrices.add(productPrice);
    }

    /**
     * Update base price and currency
     */
    public void updateBasePrice(BigDecimal newPrice, Currency newBaseCurrency) {
        if (newPrice == null || newBaseCurrency == null) {
            throw new IllegalArgumentException("Price and currency cannot be null");
        }

        this.price = newPrice;
        this.baseCurrency = newBaseCurrency;
    }

    /**
     * Check if product has price in specific currency
     */
    public boolean hasPriceInCurrency(Currency currency) {
        if (currency.equals(baseCurrency)) {
            return true;
        }

        return productPrices.stream()
                .anyMatch(pp -> pp.getCurrency().equals(currency) && pp.isActive() && pp.isEffective());
    }

    /**
     * Get all active currencies for this product
     */
    public Set<Currency> getAvailableCurrencies() {
        Set<Currency> currencies = new HashSet<>();
        currencies.add(baseCurrency);

        productPrices.stream()
                .filter(pp -> pp.isActive() && pp.isEffective())
                .forEach(pp -> currencies.add(pp.getCurrency()));

        return currencies;
    }

    /**
     * Calculate price with tax included
     */
    public BigDecimal getPriceWithTax(Currency currency) {
        BigDecimal basePrice = getPriceInCurrency(currency);
        if (basePrice == null) {
            return null;
        }

        BigDecimal taxAmount = basePrice.multiply(taxRate);
        return basePrice.add(taxAmount);
    }

    /**
     * Calculate tax amount for given currency
     */
    public BigDecimal getTaxAmount(Currency currency) {
        BigDecimal basePrice = getPriceInCurrency(currency);
        if (basePrice == null) {
            return BigDecimal.ZERO;
        }

        return basePrice.multiply(taxRate);
    }

    /**
     * Check if product is weighable
     */
    public boolean isWeighable() {
        return weighable;
    }

    /**
     * Validate quantity for this product
     */
    public boolean isValidQuantity(BigDecimal quantity) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }

        if (quantity.compareTo(minQuantity) < 0) {
            return false;
        }

        return maxQuantity == null || quantity.compareTo(maxQuantity) <= 0;
    }

    /**
     * Get formatted product name with SKU
     */
    public String getDisplayName() {
        return sku != null ? String.format("%s (%s)", name, sku) : name;
    }

    /**
     * Check if product is available for sale
     */
    public boolean isAvailableForSale() {
        return active && price != null && price.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Remove price for specific currency
     */
    public void removePriceForCurrency(Currency currency) {
        if (currency.equals(baseCurrency)) {
            throw new IllegalArgumentException("Cannot remove base currency price");
        }

        productPrices.removeIf(pp -> pp.getCurrency().equals(currency));
    }

    /**
     * Deactivate all prices for specific currency
     */
    public void deactivatePricesForCurrency(Currency currency) {
        productPrices.stream()
                .filter(pp -> pp.getCurrency().equals(currency))
                .forEach(pp -> pp.setActive(false));
    }
}