package com.pos_onlineshop.hybrid.assetDisposal;

import com.pos_onlineshop.hybrid.fixedAsset.FixedAsset;
import com.pos_onlineshop.hybrid.journalEntry.JournalEntry;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Records the one-time removal of a FixedAsset from the books - see
 * FixedAssetService.disposeAsset for the GL mechanics (clears 1500/1590 for this asset,
 * recognizes gain/loss on 5950). FixedAsset.status flips to DISPOSED atomically with this
 * record, so a disposed asset can never be disposed a second time (canBeDisposed() guards
 * it) - this table exists purely as the audit record of how and why, not as a state machine.
 */
@Entity
@Table(name = "asset_disposals")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"asset", "postedJournalEntry"})
@ToString(exclude = {"asset", "postedJournalEntry"})
public class AssetDisposal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false, unique = true)
    private FixedAsset asset;

    @Column(name = "disposal_date", nullable = false)
    private LocalDate disposalDate;

    @Column(name = "proceeds_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal proceedsAmount;

    @Column(name = "net_book_value_at_disposal", nullable = false, precision = 19, scale = 4)
    private BigDecimal netBookValueAtDisposal;

    /** Positive is a gain, negative a loss - proceedsAmount - netBookValueAtDisposal. */
    @Column(name = "gain_loss", nullable = false, precision = 19, scale = 4)
    private BigDecimal gainLoss;

    @Column(length = 500)
    private String reason;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "posted_journal_entry_id", nullable = false)
    private JournalEntry postedJournalEntry;
}
