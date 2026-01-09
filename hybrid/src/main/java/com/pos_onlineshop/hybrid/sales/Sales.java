package com.pos_onlineshop.hybrid.sales;

import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.customers.Customers;
import com.pos_onlineshop.hybrid.enums.PaymentMethod;
import com.pos_onlineshop.hybrid.enums.SaleType;
import com.pos_onlineshop.hybrid.products.Product;
import com.pos_onlineshop.hybrid.shop.Shop;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Sales entity representing sales transactions in the system.
 * Tracks individual sales with customer, product, and payment details.
 */
@Entity
@Table(name = "sales")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"shop", "customer", "product", "currency"})
@ToString(exclude = {"shop", "customer", "product", "currency"})
public class Sales {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_id", nullable = false)
    private Shop shop;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customers customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    @Builder.Default
    private Integer quantity = 1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "currency_id", nullable = false)
    private Currency currency;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "sale_type", nullable = false)
    @Builder.Default
    private SaleType saleType = SaleType.CASH;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime addedAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Calculate total price (quantity * unit price)
     */
    public BigDecimal getTotalPrice() {
        if (unitPrice != null && quantity != null) {
            return unitPrice.multiply(BigDecimal.valueOf(quantity));
        }
        return BigDecimal.ZERO;
    }

    /**
     * Check if this is a credit sale
     */
    public boolean isCreditSale() {
        return saleType == SaleType.CREDIT;
    }

    /**
     * Check if this is a cash sale
     */
    public boolean isCashSale() {
        return saleType == SaleType.CASH;
    }

    /**
     * Get sale information formatted as a string
     */
    public String getSaleInfo() {
        return String.format("Sale #%d - %s x %d @ %s %s (Total: %s %s)",
                id,
                product != null ? product.getName() : "Unknown Product",
                quantity,
                unitPrice,
                currency != null ? currency.getCode() : "USD",
                getTotalPrice(),
                currency != null ? currency.getCode() : "USD");
    }
}
