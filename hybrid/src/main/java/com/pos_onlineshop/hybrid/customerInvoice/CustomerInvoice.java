package com.pos_onlineshop.hybrid.customerInvoice;

import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.customerInvoiceLine.CustomerInvoiceLine;
import com.pos_onlineshop.hybrid.customers.Customers;
import com.pos_onlineshop.hybrid.enums.CustomerInvoiceStatus;
import com.pos_onlineshop.hybrid.shop.Shop;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A standalone credit-sale invoice - independent of Order/OrderLine. The existing POS and
 * online checkout flows always post as an immediately-paid sale (see POSService/OrderService);
 * neither has a credit/unpaid concept, so there is nothing a CustomerInvoice could
 * double-post against, unlike SupplierInvoice's PO-linked case. CustomerInvoice.post() always
 * posts CUSTOMER_INVOICE to the GL.
 */
@Entity
@Table(name = "customer_invoices")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"lines", "customer", "shop", "currency"})
@ToString(exclude = {"lines", "customer", "shop", "currency"})
public class CustomerInvoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "invoice_number", nullable = false, unique = true, length = 60)
    private String invoiceNumber;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customers customer;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "shop_id", nullable = false)
    private Shop shop;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "currency_id", nullable = false)
    private Currency currency;

    @Column(name = "exchange_rate", precision = 19, scale = 6)
    @Builder.Default
    private BigDecimal exchangeRate = BigDecimal.ONE;

    @Column(name = "invoice_date", nullable = false)
    private LocalDate invoiceDate;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "subtotal_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal subtotalAmount;

    @Column(name = "tax_amount", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalAmount;

    @Column(name = "amount_paid", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal amountPaid = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private CustomerInvoiceStatus status = CustomerInvoiceStatus.DRAFT;

    @Column(length = 1000)
    private String notes;

    @Column(name = "voided_reason", length = 500)
    private String voidedReason;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<CustomerInvoiceLine> lines = new ArrayList<>();

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void addLine(CustomerInvoiceLine line) {
        lines.add(line);
        line.setInvoice(this);
    }

    public BigDecimal getOutstandingAmount() {
        return totalAmount.subtract(amountPaid);
    }

    public boolean canBePosted() {
        return status == CustomerInvoiceStatus.DRAFT;
    }

    public boolean canReceivePayment() {
        return status == CustomerInvoiceStatus.POSTED || status == CustomerInvoiceStatus.PARTIALLY_PAID;
    }

    public boolean canBeVoided() {
        return status == CustomerInvoiceStatus.DRAFT
                || (status == CustomerInvoiceStatus.POSTED && amountPaid.compareTo(BigDecimal.ZERO) == 0);
    }

    public void post() {
        if (!canBePosted()) {
            throw new IllegalStateException("Invoice " + invoiceNumber + " cannot be posted from status " + status);
        }
        this.status = CustomerInvoiceStatus.POSTED;
    }

    public void voidInvoice(String reason) {
        if (!canBeVoided()) {
            throw new IllegalStateException(
                    "Invoice " + invoiceNumber + " cannot be voided from status " + status
                            + (amountPaid.compareTo(BigDecimal.ZERO) > 0 ? " (payments already applied)" : ""));
        }
        this.status = CustomerInvoiceStatus.VOID;
        this.voidedReason = reason;
    }

    /** Applies a receipt amount; throws if it would exceed the outstanding balance. */
    public void applyPayment(BigDecimal amount) {
        if (!canReceivePayment()) {
            throw new IllegalStateException("Invoice " + invoiceNumber + " cannot receive payment in status " + status);
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Receipt amount must be positive");
        }
        if (amount.compareTo(getOutstandingAmount()) > 0) {
            throw new IllegalArgumentException(
                    "Receipt of " + amount + " exceeds outstanding balance of " + getOutstandingAmount()
                            + " on invoice " + invoiceNumber);
        }
        this.amountPaid = this.amountPaid.add(amount);
        this.status = getOutstandingAmount().compareTo(BigDecimal.ZERO) == 0
                ? CustomerInvoiceStatus.PAID
                : CustomerInvoiceStatus.PARTIALLY_PAID;
    }
}
