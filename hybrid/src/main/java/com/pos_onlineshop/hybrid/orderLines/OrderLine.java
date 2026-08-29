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

    /**
     * Tax embedded in unitPrice (which is itself tax-inclusive - see SellingPrice), scaled by
     * quantity. Populated by copyProductDetails from the SellingPrice's actual configured taxes
     * at the time of sale. This is an amount, not a rate: Order.recalculateTotal sums this
     * directly into Order.taxAmount rather than multiplying it onto an already tax-inclusive
     * subtotal, which would double-count the tax already inside unitPrice.
     */
    @Column(name = "tax_amount", precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal taxAmount = BigDecimal.ZERO;

    /** Per-unit cost basis at the moment of sale (the latest-received-lot unit cost POSService
     * used for this line's share of COGS) - null when the cost wasn't known at sale time (see
     * POSService.processQuickSale's costKnownForAllLines). Lets SalesReturnService reverse
     * COGS/inventory accurately for a partial return instead of guessing from today's cost. */
    @Column(name = "unit_cost", precision = 19, scale = 4)
    private BigDecimal unitCost;

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

        // Tax is already baked into sellingPrice.getSellingPrice() (see
        // SellingPrice.calculateSellingPriceFromBaseAndTaxes). Recover the embedded tax amount
        // from the same basePrice + taxes that produced it, so Order.taxAmount can report what
        // was actually charged instead of staying at zero. If basePrice or taxes aren't set on
        // this price record, the tax breakdown genuinely isn't known - leave it at zero rather
        // than guess.
        BigDecimal basePrice = sellingPrice.getBasePrice();
        int quantity = this.quantity != null ? this.quantity : 0;
        if (basePrice != null && sellingPrice.getTaxes() != null && !sellingPrice.getTaxes().isEmpty()) {
            BigDecimal taxPerUnit = BigDecimal.ZERO;
            for (com.pos_onlineshop.hybrid.tax.Tax tax : sellingPrice.getTaxes()) {
                if (tax != null && Boolean.TRUE.equals(tax.getActive())) {
                    taxPerUnit = taxPerUnit.add(tax.calculateTaxAmount(basePrice));
                }
            }
            this.taxAmount = taxPerUnit.multiply(BigDecimal.valueOf(quantity));
        } else {
            this.taxAmount = BigDecimal.ZERO;
        }
    }

    /**
     * Copy product details with a specified unit price
     * Overloaded method for cases where SellingPrice is not available
     */
    public void copyProductDetails(Product product, Currency currency, BigDecimal unitPrice) {
        this.product = product;
        this.productName = product.getName();
        this.productDescription = product.getDescription();
        this.currency = currency;
        this.unitPrice = unitPrice;
    }
}