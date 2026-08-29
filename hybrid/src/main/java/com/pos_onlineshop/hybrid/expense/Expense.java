package com.pos_onlineshop.hybrid.expense;

import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.employee.Employee;
import com.pos_onlineshop.hybrid.enums.ExpensePayeeType;
import com.pos_onlineshop.hybrid.enums.ExpenseStatus;
import com.pos_onlineshop.hybrid.enums.PaymentMethod;
import com.pos_onlineshop.hybrid.expenseCategory.ExpenseCategory;
import com.pos_onlineshop.hybrid.journalEntry.JournalEntry;
import com.pos_onlineshop.hybrid.shop.Shop;
import com.pos_onlineshop.hybrid.suppliers.Suppliers;
import com.pos_onlineshop.hybrid.userAccount.UserAccount;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * An expense paid immediately (cash or bank) once approved - a petty-cash purchase, an
 * employee reimbursement, or a small supplier expense settled on the spot rather than
 * invoiced on account. Deliberately NOT for an unpaid, on-account supplier expense - that is
 * exactly what SupplierInvoice + SupplierPaymentService already model, and duplicating that
 * path here would fragment the AP subledger across two places. DRAFT -> SUBMITTED -> PAID
 * (approval and payment are the same action - see ExpenseService.approveAndPay) or REJECTED;
 * the preparer cannot approve their own expense, mirroring ManualJournal's maker-checker rule.
 */
@Entity
@Table(name = "expenses")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"category", "supplier", "employee", "currency", "shop", "createdBy", "approvedBy", "postedJournalEntry"})
@ToString(exclude = {"category", "supplier", "employee", "currency", "shop", "createdBy", "approvedBy", "postedJournalEntry"})
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "expense_number", nullable = false, unique = true, length = 60)
    private String expenseNumber;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "category_id", nullable = false)
    private ExpenseCategory category;

    @Column(nullable = false, length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "payee_type", nullable = false)
    private ExpensePayeeType payeeType;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "supplier_id")
    private Suppliers supplier;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    /** Free-text payee name when payeeType is OTHER, or a display fallback otherwise. */
    @Column(name = "payee_name", length = 150)
    private String payeeName;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "currency_id", nullable = false)
    private Currency currency;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "tax_amount", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    private PaymentMethod paymentMethod;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "shop_id")
    private Shop shop;

    @Column(name = "attachment_reference", length = 300)
    private String attachmentReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ExpenseStatus status = ExpenseStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id", nullable = false)
    private UserAccount createdBy;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_id")
    private UserAccount approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "posted_journal_entry_id")
    private JournalEntry postedJournalEntry;

    public BigDecimal getTotalAmount() {
        return amount.add(taxAmount);
    }

    public boolean canBeSubmitted() {
        return status == ExpenseStatus.DRAFT;
    }

    public boolean canBeApprovedOrRejected() {
        return status == ExpenseStatus.SUBMITTED;
    }

    public void submit() {
        if (!canBeSubmitted()) {
            throw new IllegalStateException("Expense " + expenseNumber + " cannot be submitted from status " + status);
        }
        this.status = ExpenseStatus.SUBMITTED;
    }

    public void approveAndPay(UserAccount approver, JournalEntry entry) {
        if (!canBeApprovedOrRejected()) {
            throw new IllegalStateException("Expense " + expenseNumber + " cannot be approved from status " + status);
        }
        if (createdBy != null && approver != null && createdBy.getId().equals(approver.getId())) {
            throw new IllegalStateException(
                    "Maker-checker violation: the preparer of expense " + expenseNumber + " cannot also approve it");
        }
        this.status = ExpenseStatus.PAID;
        this.approvedBy = approver;
        this.approvedAt = LocalDateTime.now();
        this.postedJournalEntry = entry;
    }

    public void reject(UserAccount approver, String reason) {
        if (!canBeApprovedOrRejected()) {
            throw new IllegalStateException("Expense " + expenseNumber + " cannot be rejected from status " + status);
        }
        this.status = ExpenseStatus.REJECTED;
        this.approvedBy = approver;
        this.approvedAt = LocalDateTime.now();
        this.rejectionReason = reason;
    }
}
