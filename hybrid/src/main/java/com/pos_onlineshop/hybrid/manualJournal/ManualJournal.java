package com.pos_onlineshop.hybrid.manualJournal;

import com.pos_onlineshop.hybrid.enums.ManualJournalStatus;
import com.pos_onlineshop.hybrid.gl.JournalImbalanceException;
import com.pos_onlineshop.hybrid.journalEntry.JournalEntry;
import com.pos_onlineshop.hybrid.manualJournalLine.ManualJournalLine;
import com.pos_onlineshop.hybrid.userAccount.UserAccount;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A user-authored journal awaiting maker-checker review before it becomes a real GL
 * JournalEntry. Unlike every other FinancialEvent producer in this codebase, a manual
 * journal's accounts and amounts are chosen freely by the preparer rather than derived
 * from a PostingRule - see GLPostingRuleSeedService's class comment for why MANUAL_ENTRY
 * has no seeded rule. GLPostingService.postManual() is the only path that turns an
 * APPROVED ManualJournal into a POSTED JournalEntry; that JournalEntry's sourceModule is
 * MANUAL, so JournalValidator's control-account rule already blocks any line here from
 * touching a control account.
 *
 * Workflow: DRAFT -> SUBMITTED -> APPROVED -> POSTED, or SUBMITTED -> REJECTED. The
 * approver must not be the preparer (submittedBy) - enforced in approve().
 */
@Entity
@Table(name = "manual_journals")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"lines", "createdBy", "submittedBy", "approvedBy", "rejectedBy", "postedJournalEntry"})
@ToString(exclude = {"lines", "createdBy", "submittedBy", "approvedBy", "rejectedBy", "postedJournalEntry"})
public class ManualJournal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @Column(length = 500, nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ManualJournalStatus status = ManualJournalStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id", nullable = false)
    private UserAccount createdBy;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submitted_by_id")
    private UserAccount submittedBy;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_id")
    private UserAccount approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rejected_by_id")
    private UserAccount rejectedBy;

    @Column(name = "rejected_at")
    private LocalDateTime rejectedAt;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "posted_journal_entry_id")
    private JournalEntry postedJournalEntry;

    /** e.g. "attachments/2026/08/invoice-scan.pdf" via the existing StoredFiles infra - free text
     * for now since ManualJournal predates a formal attachment linkage (see Phase 45). */
    @Column(name = "attachment_reference", length = 300)
    private String attachmentReference;

    @OneToMany(mappedBy = "manualJournal", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<ManualJournalLine> lines = new ArrayList<>();

    public void addLine(ManualJournalLine line) {
        lines.add(line);
        line.setManualJournal(this);
    }

    public boolean canBeSubmitted() {
        return status == ManualJournalStatus.DRAFT;
    }

    public boolean canBeApprovedOrRejected() {
        return status == ManualJournalStatus.SUBMITTED;
    }

    public boolean canBePosted() {
        return status == ManualJournalStatus.APPROVED;
    }

    /** At least two lines, each with exactly one of debit/credit set, and total debits ==
     * total credits. Run at submit() and again at post() as defense in depth. */
    public void validateBalance() {
        if (lines == null || lines.size() < 2) {
            throw new JournalImbalanceException(
                    "A manual journal needs at least two lines, got " + (lines == null ? 0 : lines.size()));
        }
        BigDecimal totalDebits = BigDecimal.ZERO;
        BigDecimal totalCredits = BigDecimal.ZERO;
        for (ManualJournalLine line : lines) {
            BigDecimal debit = line.getDebitAmount() == null ? BigDecimal.ZERO : line.getDebitAmount();
            BigDecimal credit = line.getCreditAmount() == null ? BigDecimal.ZERO : line.getCreditAmount();
            if (debit.compareTo(BigDecimal.ZERO) < 0 || credit.compareTo(BigDecimal.ZERO) < 0) {
                throw new JournalImbalanceException("A manual journal line cannot carry a negative amount");
            }
            boolean hasDebit = debit.compareTo(BigDecimal.ZERO) > 0;
            boolean hasCredit = credit.compareTo(BigDecimal.ZERO) > 0;
            if (hasDebit == hasCredit) {
                throw new JournalImbalanceException(
                        "Each manual journal line must have exactly one of debit/credit set, not "
                                + (hasDebit ? "both" : "neither"));
            }
            totalDebits = totalDebits.add(debit);
            totalCredits = totalCredits.add(credit);
        }
        if (totalDebits.compareTo(totalCredits) != 0) {
            throw new JournalImbalanceException(
                    "Manual journal does not balance: debits=" + totalDebits + " credits=" + totalCredits);
        }
    }

    public void submit(UserAccount submittedByUser) {
        if (!canBeSubmitted()) {
            throw new IllegalStateException("Manual journal " + id + " cannot be submitted from status " + status);
        }
        validateBalance();
        this.status = ManualJournalStatus.SUBMITTED;
        this.submittedBy = submittedByUser;
        this.submittedAt = LocalDateTime.now();
    }

    public void approve(UserAccount approvedByUser) {
        if (!canBeApprovedOrRejected()) {
            throw new IllegalStateException("Manual journal " + id + " cannot be approved from status " + status);
        }
        if (createdBy != null && approvedByUser != null && createdBy.getId().equals(approvedByUser.getId())) {
            throw new IllegalStateException(
                    "Maker-checker violation: the preparer of manual journal " + id + " cannot also approve it");
        }
        this.status = ManualJournalStatus.APPROVED;
        this.approvedBy = approvedByUser;
        this.approvedAt = LocalDateTime.now();
    }

    public void reject(UserAccount rejectedByUser, String reason) {
        if (!canBeApprovedOrRejected()) {
            throw new IllegalStateException("Manual journal " + id + " cannot be rejected from status " + status);
        }
        this.status = ManualJournalStatus.REJECTED;
        this.rejectedBy = rejectedByUser;
        this.rejectedAt = LocalDateTime.now();
        this.rejectionReason = reason;
    }

    public void markPosted(JournalEntry entry) {
        if (!canBePosted()) {
            throw new IllegalStateException("Manual journal " + id + " cannot be posted from status " + status);
        }
        this.status = ManualJournalStatus.POSTED;
        this.postedJournalEntry = entry;
    }
}
