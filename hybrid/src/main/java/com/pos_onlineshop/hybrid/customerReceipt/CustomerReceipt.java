package com.pos_onlineshop.hybrid.customerReceipt;

import com.pos_onlineshop.hybrid.cashier.Cashier;
import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.customerInvoice.CustomerInvoice;
import com.pos_onlineshop.hybrid.customers.Customers;
import com.pos_onlineshop.hybrid.enums.PaymentMethod;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** A receipt against exactly one CustomerInvoice - see SupplierPaymentService's equivalent
 * note on multi-invoice allocation (same limitation, not implemented). */
@Entity
@Table(name = "customer_receipts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"customer", "invoice", "currency", "recordedBy"})
@ToString(exclude = {"customer", "invoice", "currency", "recordedBy"})
public class CustomerReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customers customer;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "customer_invoice_id", nullable = false)
    private CustomerInvoice invoice;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "currency_id", nullable = false)
    private Currency currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    private PaymentMethod paymentMethod;

    @Column(name = "receipt_date", nullable = false)
    @Builder.Default
    private LocalDate receiptDate = LocalDate.now();

    @Column(length = 100)
    private String reference;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "recorded_by")
    private Cashier recordedBy;

    @Column(length = 500)
    private String notes;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
