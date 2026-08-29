package com.pos_onlineshop.hybrid.openingBalance;

import com.pos_onlineshop.hybrid.journalEntry.JournalEntry;
import com.pos_onlineshop.hybrid.userAccount.UserAccount;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Audit header for a one-time opening-balance posting (go-live conversion, or bringing a
 * new legal entity/account onto the books mid-year). The actual debit/credit lines live on
 * the linked postedJournalEntry - see OpeningBalanceService for why this posts straight
 * through GLPostingService.postManual(sourceModule=OPENING_BALANCE) rather than through the
 * ManualJournal maker-checker pipeline: JournalValidator blocks MANUAL-sourced lines from
 * touching a control account (1100/1200/2100), which is exactly what an opening balance
 * must do. reference is caller-supplied and unique, not the auto-generated id, because it
 * doubles as the GL idempotency key ("OPENING-BALANCE-" + reference) and must be known
 * before the first save to make a client retry safe.
 */
@Entity
@Table(name = "opening_balance_entries")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"createdBy", "postedJournalEntry"})
@ToString(exclude = {"createdBy", "postedJournalEntry"})
public class OpeningBalanceEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String reference;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @Column(length = 500, nullable = false)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id", nullable = false)
    private UserAccount createdBy;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "posted_journal_entry_id", nullable = false)
    private JournalEntry postedJournalEntry;
}
