package com.pos_onlineshop.hybrid.ias29Restatement;

import com.pos_onlineshop.hybrid.fixedAsset.FixedAsset;
import com.pos_onlineshop.hybrid.journalEntry.JournalEntry;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Audit record of one IAS 29 price-level restatement of a single FixedAsset - see
 * Ias29RestatementService's class comment for the full mechanism. Records exactly the
 * movement this restatement recognized (prior -> new restated cost and accumulated
 * depreciation), the same "diff since last restatement, not re-derive from the current
 * value" pattern FxRevaluationEntry uses for FX. Never edited once posted - a mistake is
 * corrected via reverse(), which posts a contra entry and flags this row reversed, rather
 * than rewriting it.
 */
@Entity
@Table(name = "ias29_restatement_entries")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"fixedAsset", "postedJournalEntry", "reversalJournalEntry"})
@ToString(exclude = {"fixedAsset", "postedJournalEntry", "reversalJournalEntry"})
public class Ias29RestatementEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fixed_asset_id", nullable = false)
    private FixedAsset fixedAsset;

    @Column(name = "restatement_date", nullable = false)
    private LocalDate restatementDate;

    /** Gross cost this asset was carried at before this restatement - either
     * FixedAsset.acquisitionCost (first restatement) or the newRestatedCost of this same
     * asset's most recent prior restatement. */
    @Column(name = "prior_restated_cost", nullable = false, precision = 19, scale = 4)
    private BigDecimal priorRestatedCost;

    @Column(name = "new_restated_cost", nullable = false, precision = 19, scale = 4)
    private BigDecimal newRestatedCost;

    @Column(name = "prior_restated_accumulated_depreciation", nullable = false, precision = 19, scale = 4)
    private BigDecimal priorRestatedAccumulatedDepreciation;

    @Column(name = "new_restated_accumulated_depreciation", nullable = false, precision = 19, scale = 4)
    private BigDecimal newRestatedAccumulatedDepreciation;

    /** Net book value movement recognized in 3910 IAS 29 Restatement Reserve - positive is
     * an increase (the normal case under inflation), negative a decrease. */
    @Column(name = "net_adjustment", nullable = false, precision = 19, scale = 4)
    private BigDecimal netAdjustment;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "posted_journal_entry_id", nullable = false)
    private JournalEntry postedJournalEntry;

    @Column(nullable = false)
    @Builder.Default
    private boolean reversed = false;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reversal_journal_entry_id")
    private JournalEntry reversalJournalEntry;

    @Column(name = "reversal_reason")
    private String reversalReason;

    @Column(name = "reversed_at")
    private LocalDateTime reversedAt;
}
