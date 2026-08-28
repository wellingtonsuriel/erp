package com.pos_onlineshop.hybrid.journalEntry;

import com.pos_onlineshop.hybrid.accountingPeriod.AccountingPeriod;
import com.pos_onlineshop.hybrid.enums.GLSourceModule;
import com.pos_onlineshop.hybrid.enums.JournalStatus;
import com.pos_onlineshop.hybrid.journalLine.JournalLine;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Header of a double-entry journal entry. Once status is POSTED this row and its lines
 * are never updated or deleted again - a correction is a new entry created by
 * GLPostingService.reverseJournalEntry, linked via reversalOfEntry / reversedByEntry.
 */
@Entity
@Table(name = "gl_journal_entries")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"lines", "accountingPeriod", "reversalOfEntry", "reversedByEntry"})
@ToString(exclude = {"lines", "accountingPeriod", "reversalOfEntry", "reversedByEntry"})
public class JournalEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Sequential, gapless per legal entity - allocated by GLNumberingService inside the posting transaction. */
    @Column(name = "entry_number", nullable = false, unique = true)
    private Long entryNumber;

    /** e.g. "POS-SALE-{orderId}". Unique at the DB level so a retried request cannot double-post. */
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 150)
    private String idempotencyKey;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "accounting_period_id", nullable = false)
    private AccountingPeriod accountingPeriod;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_module", nullable = false)
    private GLSourceModule sourceModule;

    @Column(name = "source_reference_type", length = 60)
    private String sourceReferenceType;

    @Column(name = "source_reference_id")
    private Long sourceReferenceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private JournalStatus status = JournalStatus.POSTED;

    @Column(name = "posted_by", length = 100)
    private String postedBy;

    @Column(name = "posted_at")
    private LocalDateTime postedAt;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reversal_of_entry_id")
    private JournalEntry reversalOfEntry;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reversed_by_entry_id")
    private JournalEntry reversedByEntry;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @OneToMany(mappedBy = "journalEntry", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<JournalLine> lines = new ArrayList<>();

    public void addLine(JournalLine line) {
        lines.add(line);
        line.setJournalEntry(this);
    }
}
