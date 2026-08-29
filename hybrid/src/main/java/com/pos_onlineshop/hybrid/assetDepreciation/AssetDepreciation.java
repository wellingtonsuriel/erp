package com.pos_onlineshop.hybrid.assetDepreciation;

import com.pos_onlineshop.hybrid.fixedAsset.FixedAsset;
import com.pos_onlineshop.hybrid.journalEntry.JournalEntry;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One posted depreciation run for one asset for one period - the audit trail
 * FixedAssetService.runMonthlyDepreciation relies on to never depreciate the same
 * asset/period twice (see its class comment), and the record a "depreciation schedule" view
 * would read. Never edited or deleted once posted - a correction is a new period's run, same
 * as every other posted GL-linked record in this codebase.
 */
@Entity
@Table(name = "asset_depreciations", uniqueConstraints = @UniqueConstraint(columnNames = {"asset_id", "period_date"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"asset", "postedJournalEntry"})
@ToString(exclude = {"asset", "postedJournalEntry"})
public class AssetDepreciation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private FixedAsset asset;

    /** The period this depreciation charge belongs to - the last day of the depreciated
     * month, matching AccountingPeriod's own month-end convention. */
    @Column(name = "period_date", nullable = false)
    private LocalDate periodDate;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    /** Accumulated depreciation on this asset immediately after this run - a point-in-time
     * snapshot for the schedule view, since FixedAsset.accumulatedDepreciation itself keeps
     * advancing. */
    @Column(name = "accumulated_depreciation_after", nullable = false, precision = 19, scale = 4)
    private BigDecimal accumulatedDepreciationAfter;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "posted_journal_entry_id", nullable = false)
    private JournalEntry postedJournalEntry;
}
