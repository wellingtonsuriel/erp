package com.pos_onlineshop.hybrid.orders;

import com.pos_onlineshop.hybrid.cashier.Cashier;
import com.pos_onlineshop.hybrid.cashierSessions.CashierSession;
import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.customers.Customers;
import com.pos_onlineshop.hybrid.enums.OrderStatus;
import com.pos_onlineshop.hybrid.enums.PaymentMethod;
import com.pos_onlineshop.hybrid.enums.SalesChannel;
import com.pos_onlineshop.hybrid.orderLines.OrderLine;
import com.pos_onlineshop.hybrid.shop.Shop;
import com.pos_onlineshop.hybrid.userAccount.UserAccount;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"orderLines", "user", "customer", "currency", "shop", "cashier", "cashierSession"})
@ToString(exclude = {"orderLines", "user", "customer", "currency", "shop", "cashier", "cashierSession"})
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private UserAccount user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customers customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "currency_id", nullable = false)
    private Currency currency; // Order currency

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OrderLine> orderLines = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private OrderStatus status = OrderStatus.PENDING;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "tax_amount", precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "exchange_rate", precision = 19, scale = 6)
    private BigDecimal exchangeRate; // Rate used if converted from base currency

    @Column(name = "order_date", nullable = false)
    @Builder.Default
    private LocalDateTime orderDate = LocalDateTime.now();

    @Column(name = "shipping_address")
    private String shippingAddress;

    @Column(name = "payment_method")
    @Enumerated(EnumType.STRING)
    private PaymentMethod paymentMethod;

    // Hybrid fields
    @Column(name = "sales_channel")
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private SalesChannel salesChannel = SalesChannel.ONLINE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_id")
    private Shop shop;

    @Column(name = "store_location")
    private String storeLocation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cashier_id")
    private Cashier cashier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cashier_session_id")
    private CashierSession cashierSession;

    @Column(name = "receipt_number")
    private String receiptNumber;

    @Column(name = "cash_given", precision = 19, scale = 2)
    private BigDecimal cashGiven;

    @Column(name = "change_amount", precision = 19, scale = 2)
    private BigDecimal changeAmount;

    @Column(name = "is_pickup")
    @Builder.Default
    private boolean isPickup = false;

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

    // Business methods
    public void addOrderLine(OrderLine line) {
        orderLines.add(line);
        line.setOrder(this);
        recalculateTotal();
    }

    public void removeOrderLine(OrderLine line) {
        orderLines.remove(line);
        line.setOrder(null);
        recalculateTotal();
    }

    public void recalculateTotal() {
        this.totalAmount = orderLines.stream()
                .map(OrderLine::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        this.taxAmount = orderLines.stream()
                .map(line -> line.getSubtotal().multiply(line.getTaxRate()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        this.totalAmount = this.totalAmount.add(this.taxAmount);
    }
}