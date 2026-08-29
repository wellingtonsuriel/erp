package com.pos_onlineshop.hybrid.fixedAsset;

import com.pos_onlineshop.hybrid.enums.DepreciationMethod;
import com.pos_onlineshop.hybrid.enums.FixedAssetStatus;
import com.pos_onlineshop.hybrid.fixedAssetCategory.FixedAssetCategory;
import com.pos_onlineshop.hybrid.journalEntry.JournalEntry;
import com.pos_onlineshop.hybrid.shop.Shop;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A registered fixed asset - acquisition cost, useful life, and residual value are fixed at
 * registration (correcting a mistake here is a new transaction, not an edit, matching every
 * other posted-document entity in this codebase); accumulatedDepreciation is the one mutable
 * field, advanced only by FixedAssetService.runMonthlyDepreciation. netBookValue is always
 * acquisitionCost - accumulatedDepreciation, computed rather than stored, so it can never
 * drift out of sync with the two numbers it derives from.
 */
@Entity
@Table(name = "fixed_assets")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"category", "shop", "acquisitionJournalEntry"})
@ToString(exclude = {"category", "shop", "acquisitionJournalEntry"})
public class FixedAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "asset_number", nullable = false, unique = true, length = 60)
    private String assetNumber;

    @Column(nullable = false, length = 150)
    private String name;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "category_id", nullable = false)
    private FixedAssetCategory category;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "shop_id")
    private Shop shop;

    @Column(name = "acquisition_date", nullable = false)
    private LocalDate acquisitionDate;

    @Column(name = "acquisition_cost", nullable = false, precision = 19, scale = 4)
    private BigDecimal acquisitionCost;

    @Column(name = "useful_life_months", nullable = false)
    private Integer usefulLifeMonths;

    @Column(name = "residual_value", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal residualValue = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "depreciation_method", nullable = false)
    @Builder.Default
    private DepreciationMethod depreciationMethod = DepreciationMethod.STRAIGHT_LINE;

    @Column(name = "accumulated_depreciation", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal accumulatedDepreciation = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private FixedAssetStatus status = FixedAssetStatus.ACTIVE;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    /** The GL entry that recorded this asset's acquisition (Dr 1500 Fixed Assets / Cr 2100
     * Accounts Payable) - posted atomically with registration, see FixedAssetService. */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "acquisition_journal_entry_id")
    private JournalEntry acquisitionJournalEntry;

    public BigDecimal getNetBookValue() {
        return acquisitionCost.subtract(accumulatedDepreciation);
    }

    public BigDecimal getDepreciableBase() {
        return acquisitionCost.subtract(residualValue);
    }

    public boolean isFullyDepreciated() {
        return accumulatedDepreciation.compareTo(getDepreciableBase()) >= 0;
    }
}
