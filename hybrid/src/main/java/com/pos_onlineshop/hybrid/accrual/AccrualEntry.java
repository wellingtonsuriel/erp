package com.pos_onlineshop.hybrid.accrual;

import com.pos_onlineshop.hybrid.enums.AccrualStatus;
import com.pos_onlineshop.hybrid.journalEntry.JournalEntry;
import com.pos_onlineshop.hybrid.userAccount.UserAccount;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Audit header for an accrual posted now with an intended reversal in a later period
 * (the standard "accrue this period, reverse next period" pattern - e.g. accrue an
 * expense incurred but not yet invoiced, then reverse it once the real invoice posts so
 * the two don't double-count). The accrual's own lines are posted immediately through
 * GLPostingService.postManual(sourceModule=ACCRUAL); the reversal, when due, is done via
 * the existing GLPostingService.reverse() - it already flips every line of a POSTED entry
 * and is idempotent, so this header only needs to track *whether* and *when* reversal
 * happened, not how.
 */
@Entity
@Table(name = "accrual_entries")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"createdBy", "postedJournalEntry", "reversalJournalEntry"})
@ToString(exclude = {"createdBy", "postedJournalEntry", "reversalJournalEntry"})
public class AccrualEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String reference;

    @Column(name = "accrual_date", nullable = false)
    private LocalDate accrualDate;

    /** The date on/after which this accrual should be reversed - typically the first day
     * of the following accounting period. */
    @Column(name = "reversal_date", nullable = false)
    private LocalDate reversalDate;

    @Column(length = 500, nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private AccrualStatus status = AccrualStatus.PENDING_REVERSAL;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id", nullable = false)
    private UserAccount createdBy;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "posted_journal_entry_id", nullable = false)
    private JournalEntry postedJournalEntry;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reversal_journal_entry_id")
    private JournalEntry reversalJournalEntry;

    @Column(name = "reversed_at")
    private LocalDateTime reversedAt;

    public boolean isDueForReversal(LocalDate asOfDate) {
        return status == AccrualStatus.PENDING_REVERSAL && !reversalDate.isAfter(asOfDate);
    }
}
