package com.pos_onlineshop.hybrid.supplierInvoice;

import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.enums.SupplierInvoiceStatus;
import com.pos_onlineshop.hybrid.purchaseOrder.PurchaseOrder;
import com.pos_onlineshop.hybrid.shop.Shop;
import com.pos_onlineshop.hybrid.suppliers.Suppliers;
import com.pos_onlineshop.hybrid.supplierInvoiceLine.SupplierInvoiceLine;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A supplier's bill for goods or services. May optionally reference the PurchaseOrder it
 * relates to - see SupplierInvoiceService's class comment for why that matters to GL posting
 * (goods already received via a PO already booked the Accounts Payable liability at receipt
 * time; a PO-linked invoice is a subledger record of that liability, not a second posting).
 */
@Entity
@Table(name = "supplier_invoices")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"lines", "supplier", "shop", "currency", "purchaseOrder"})
@ToString(exclude = {"lines", "supplier", "shop", "currency", "purchaseOrder"})
public class SupplierInvoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "invoice_number", nullable = false, unique = true, length = 60)
    private String invoiceNumber;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Suppliers supplier;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "shop_id", nullable = false)
    private Shop shop;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "purchase_order_id")
    private PurchaseOrder purchaseOrder;

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
    private SupplierInvoiceStatus status = SupplierInvoiceStatus.DRAFT;

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
    private List<SupplierInvoiceLine> lines = new ArrayList<>();

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void addLine(SupplierInvoiceLine line) {
        lines.add(line);
        line.setInvoice(this);
    }

    public BigDecimal getOutstandingAmount() {
        return totalAmount.subtract(amountPaid);
    }

    public boolean isPoLinked() {
        return purchaseOrder != null;
    }

    public boolean canBePosted() {
        return status == SupplierInvoiceStatus.DRAFT;
    }

    public boolean canReceivePayment() {
        return status == SupplierInvoiceStatus.POSTED || status == SupplierInvoiceStatus.PARTIALLY_PAID;
    }

    public boolean canBeVoided() {
        return status == SupplierInvoiceStatus.DRAFT
                || (status == SupplierInvoiceStatus.POSTED && amountPaid.compareTo(BigDecimal.ZERO) == 0);
    }

    public void post() {
        if (!canBePosted()) {
            throw new IllegalStateException("Invoice " + invoiceNumber + " cannot be posted from status " + status);
        }
        this.status = SupplierInvoiceStatus.POSTED;
    }

    public void voidInvoice(String reason) {
        if (!canBeVoided()) {
            throw new IllegalStateException(
                    "Invoice " + invoiceNumber + " cannot be voided from status " + status
                            + (amountPaid.compareTo(BigDecimal.ZERO) > 0 ? " (payments already applied)" : ""));
        }
        this.status = SupplierInvoiceStatus.VOID;
        this.voidedReason = reason;
    }

    /** Applies a payment amount; throws if it would exceed the outstanding balance. */
    public void applyPayment(BigDecimal amount) {
        if (!canReceivePayment()) {
            throw new IllegalStateException("Invoice " + invoiceNumber + " cannot receive payment in status " + status);
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be positive");
        }
        if (amount.compareTo(getOutstandingAmount()) > 0) {
            throw new IllegalArgumentException(
                    "Payment of " + amount + " exceeds outstanding balance of " + getOutstandingAmount()
                            + " on invoice " + invoiceNumber);
        }
        this.amountPaid = this.amountPaid.add(amount);
        this.status = getOutstandingAmount().compareTo(BigDecimal.ZERO) == 0
                ? SupplierInvoiceStatus.PAID
                : SupplierInvoiceStatus.PARTIALLY_PAID;
    }
}
