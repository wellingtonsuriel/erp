package com.pos_onlineshop.hybrid.orderLines;

import com.pos_onlineshop.hybrid.cashier.Cashier;
import com.pos_onlineshop.hybrid.cashierSessions.CashierSession;
import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.orders.Order;
import com.pos_onlineshop.hybrid.products.Product;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "order_lines")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"order"})
@ToString(exclude = {"order"})
public class OrderLine {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne
    @JoinColumn(name = "cashier_id")
    private Cashier cashier; // For POS sales

    @ManyToOne
    @JoinColumn(name = "cashier_session_id")
    private CashierSession cashierSession;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitPrice; // In order currency

    @ManyToOne
    @JoinColumn(name = "currency_id", nullable = false)
    private Currency currency; // Currency of the unit price

    @Column(name = "tax_rate", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal taxRate = new BigDecimal("0.00");

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "product_description")
    private String productDescription;

    public BigDecimal getSubtotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    public void copyProductDetails(Product product, Currency currency) {
        this.product = product;
        this.productName = product.getName();
        this.productDescription = product.getDescription();
        this.taxRate = product.getTaxRate();
        this.currency = currency;

        // Get price in the specified currency
        BigDecimal priceInCurrency = getProductPriceInCurrency(product, currency);
        if (priceInCurrency == null) {
            throw new RuntimeException("Product price not available in currency: " + currency.getCode());
        }
        this.unitPrice = priceInCurrency;
    }

    /**
     * Overloaded method for backward compatibility - uses product's default currency
     */
    public void copyProductDetails(Product product) {
        // Use the product's default currency or the first available price
        Currency productCurrency = getProductDefaultCurrency(product);
        copyProductDetails(product, productCurrency);
    }

    /**
     * Helper method to get product price in specific currency
     */
    private BigDecimal getProductPriceInCurrency(Product product, Currency currency) {
        // Option 1: If Product has a method to get price in currency
        if (hasMethod(product, "getPriceInCurrency")) {
            return product.getPriceInCurrency(currency);
        }

        // Option 2: If Product has a base price and you need currency conversion
        if (hasMethod(product, "getPrice")) {
            BigDecimal basePrice = product.getPrice();
            // You would need to inject CurrencyService here or pass it as parameter
            // For now, return the base price (this should be enhanced with currency conversion)
            return basePrice;
        }

        // Option 3: If you have a separate ProductPrice entity
        // return productPriceService.getPriceInCurrency(product.getId(), currency.getId());

        throw new RuntimeException("Unable to determine product price for currency: " + currency.getCode());
    }

    /**
     * Helper method to get product's default currency
     */
    private Currency getProductDefaultCurrency(Product product) {
        // Option 1: If Product has a default currency
        if (hasMethod(product, "getDefaultCurrency")) {
            return product.getDefaultCurrency();
        }

        // Option 2: Use system base currency as fallback
        // You would need to inject CurrencyService for this
        throw new RuntimeException("Product default currency not available");
    }

    /**
     * Helper method to check if a method exists (using reflection)
     */
    private boolean hasMethod(Object obj, String methodName) {
        try {
            obj.getClass().getMethod(methodName);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }
}