package com.pos_onlineshop.hybrid.accountingPeriod;

import com.pos_onlineshop.hybrid.enums.PeriodStatus;
import com.pos_onlineshop.hybrid.journalEntry.JournalEntry;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "gl_accounting_periods")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"closingJournalEntry"})
@ToString(exclude = {"closingJournalEntry"})
public class AccountingPeriod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** e.g. "2026-08" */
    @Column(nullable = false, unique = true, length = 20)
    private String name;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PeriodStatus status = PeriodStatus.OPEN;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "closed_by")
    private String closedBy;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    /** The revenue/expense-to-Retained-Earnings sweep entry from the most recent close, or
     * null if this period has never been closed (or was reopened and the sweep reversed -
     * see AccountingPeriodService.reopenPeriod). Tracked so a reopen can reverse exactly this
     * entry: the sweep recomputes from every JournalLine posted within the period's own date
     * range, which includes the sweep's own prior lines, so leaving a stale sweep in place
     * would double-count it into the next close. */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "closing_journal_entry_id")
    private JournalEntry closingJournalEntry;

    /** How many times this period has been closed (reopens don't decrement it). Folded into
     * the closing entry's idempotency key ("PERIOD-CLOSE-{id}-{closeCount}") so a genuine
     * retry of the same close attempt still replays idempotently, while a close that follows
     * a reopen gets a fresh key rather than replaying the (now-reversed) prior entry. */
    @Column(name = "close_count", nullable = false)
    @Builder.Default
    private int closeCount = 0;

    public boolean acceptsPosting() {
        return status == PeriodStatus.OPEN;
    }

    public boolean containsDate(LocalDate date) {
        return !date.isBefore(startDate) && !date.isAfter(endDate);
    }
}
