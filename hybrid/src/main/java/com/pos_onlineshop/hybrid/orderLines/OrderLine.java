package com.pos_onlineshop.hybrid.orderLines;

import com.pos_onlineshop.hybrid.cashier.Cashier;
import com.pos_onlineshop.hybrid.cashierSessions.CashierSession;
import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.orders.Order;
import com.pos_onlineshop.hybrid.products.Product;
import com.pos_onlineshop.hybrid.selling_price.SellingPrice;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "order_lines")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"order", "product", "cashier", "cashierSession", "currency"})
@ToString(exclude = {"order", "product", "cashier", "cashierSession", "currency"})
public class OrderLine {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cashier_id")
    private Cashier cashier; // For POS sales

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cashier_session_id")
    private CashierSession cashierSession;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitPrice; // In order currency

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "currency_id", nullable = false)
    private Currency currency; // Currency of the unit price

    @Column(name = "tax_rate", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal taxRate = new BigDecimal("0.00");

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "product_description")
    private String productDescription;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Calculate subtotal (quantity * unit price)
     */
    public BigDecimal getSubtotal() {
        if (unitPrice != null && quantity != null) {
            return unitPrice.multiply(BigDecimal.valueOf(quantity));
        }
        return BigDecimal.ZERO;
    }

    /**
     * Calculate total with tax
     */
    public BigDecimal getTotalWithTax() {
        BigDecimal subtotal = getSubtotal();
        if (taxRate != null) {
            BigDecimal tax = subtotal.multiply(taxRate);
            return subtotal.add(tax);
        }
        return subtotal;
    }

    /**
     * Copy product details and set unit price from selling price
     */
    public void copyProductDetails(SellingPrice sellingPrice, Currency currency) {
        Product product = sellingPrice.getProduct();
        this.product = product;
        this.productName = product.getName();
        this.productDescription = product.getDescription();
        this.currency = currency;

        // Set unit price from product's selling price
        // Note: In a real scenario, you might need to convert price to the order's currency
        this.unitPrice = sellingPrice.getSellingPrice();
    }
}