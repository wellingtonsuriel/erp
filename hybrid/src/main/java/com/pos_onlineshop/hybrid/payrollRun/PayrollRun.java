package com.pos_onlineshop.hybrid.payrollRun;

import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.enums.PayrollRunStatus;
import com.pos_onlineshop.hybrid.enums.PaymentMethod;
import com.pos_onlineshop.hybrid.journalEntry.JournalEntry;
import com.pos_onlineshop.hybrid.userAccount.UserAccount;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One payroll run for a period - see PayrollService for the two-posting mechanics
 * (accrualJournalEntry: Dr 5200 Salary Expense / Cr 2400 Payroll Payable + Cr 2410 Payroll
 * Deductions Payable, posted the moment the run is processed; paymentJournalEntry: Dr 2400
 * Payroll Payable / Cr Cash or Bank for the net pay only, posted separately when actually
 * paid out - see PayrollRunStatus for why there's no DRAFT state).
 */
@Entity
@Table(name = "payroll_runs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"currency", "createdBy", "accrualJournalEntry", "paymentJournalEntry"})
@ToString(exclude = {"currency", "createdBy", "accrualJournalEntry", "paymentJournalEntry"})
public class PayrollRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_number", nullable = false, unique = true, length = 60)
    private String runNumber;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "pay_date", nullable = false)
    private LocalDate payDate;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "currency_id", nullable = false)
    private Currency currency;

    @Column(name = "total_gross_pay", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalGrossPay;

    @Column(name = "total_deductions", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalDeductions;

    @Column(name = "total_net_pay", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalNetPay;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method")
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PayrollRunStatus status = PayrollRunStatus.PROCESSED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id", nullable = false)
    private UserAccount createdBy;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    /** Set right after the run row is first saved (needs the row's id as the GL source
     * reference), same save-then-attach pattern as FixedAsset.acquisitionJournalEntry - see
     * PayrollService.processPayroll(). */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "accrual_journal_entry_id")
    private JournalEntry accrualJournalEntry;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_journal_entry_id")
    private JournalEntry paymentJournalEntry;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    public boolean canBePaid() {
        return status == PayrollRunStatus.PROCESSED;
    }
}
