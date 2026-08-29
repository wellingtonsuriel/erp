package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.account.Account;
import com.pos_onlineshop.hybrid.account.AccountRepository;
import com.pos_onlineshop.hybrid.assetDepreciation.AssetDepreciation;
import com.pos_onlineshop.hybrid.assetDepreciation.AssetDepreciationRepository;
import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.dtos.Ias29RestatementResponse;
import com.pos_onlineshop.hybrid.enums.FixedAssetStatus;
import com.pos_onlineshop.hybrid.enums.GLSourceModule;
import com.pos_onlineshop.hybrid.fixedAsset.FixedAsset;
import com.pos_onlineshop.hybrid.fixedAsset.FixedAssetRepository;
import com.pos_onlineshop.hybrid.gl.ManualLineSpec;
import com.pos_onlineshop.hybrid.ias29Restatement.Ias29RestatementEntry;
import com.pos_onlineshop.hybrid.ias29Restatement.Ias29RestatementEntryRepository;
import com.pos_onlineshop.hybrid.journalEntry.JournalEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * IAS 29 (Financial Reporting in Hyperinflationary Economies): restates FixedAsset carrying
 * values to current price levels using GeneralPriceIndexService's conversion factor - the
 * "extending the extension point" this codebase's GeneralPriceIndexService class comment
 * explicitly deferred, now that the acquisition-date data it needs actually exists (every
 * FixedAsset already carries acquisitionDate, and every AssetDepreciation charge already
 * carries the periodDate it was posted in).
 *
 * Gross cost and accumulated depreciation are restated SEPARATELY, not as one scaled
 * netBookValue: acquisitionCost is restated using the factor from acquisitionDate, but each
 * depreciation charge is restated using the factor from ITS OWN periodDate - a charge posted
 * three years after acquisition has three fewer years of inflation to catch up on than the
 * asset's original cost does. Collapsing that into a single acquisitionDate-based factor
 * applied to netBookValue would overstate accumulated depreciation's restatement and
 * understate the net adjustment.
 *
 * "Avoid repeated restatement errors" (never compounding the same index movement twice): the
 * same pattern FxRevaluationService uses for exchange rates - FixedAsset.restatedCost/
 * restatedAccumulatedDepreciation ARE the carrying restated values (null means "never
 * restated," i.e. still at historical cost), and every run diffs against THOSE, not against
 * the historical figures a second time.
 *
 * Known limitations, same honesty as GeneralPriceIndexService's own comment: this restates
 * Fixed Assets only. Inventory and Equity are also non-monetary under IAS 29 but this
 * codebase has no acquisition-date tracking at the lot/movement level for either yet (see
 * GeneralPriceIndexService's class comment) - restating them would mean guessing an
 * acquisition date, which this system refuses to do. Net monetary gain/loss (the P&L
 * consequence of holding a net monetary position through the period) is not computed here
 * either, for the same reason GeneralPriceIndexService cites: it requires tracking net
 * monetary position through the period, not just its endpoints.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class Ias29RestatementService {

    private static final String FIXED_ASSETS_ACCOUNT_CODE = "1500";
    private static final String ACCUMULATED_DEPRECIATION_ACCOUNT_CODE = "1590";
    private static final String RESTATEMENT_RESERVE_ACCOUNT_CODE = "3910";

    private final FixedAssetRepository fixedAssetRepository;
    private final AssetDepreciationRepository assetDepreciationRepository;
    private final Ias29RestatementEntryRepository ias29RestatementEntryRepository;
    private final AccountRepository accountRepository;
    private final GLPostingService glPostingService;
    private final GeneralPriceIndexService generalPriceIndexService;
    private final CurrencyService currencyService;

    @Transactional(readOnly = true)
    public List<Ias29RestatementResponse> findAll() {
        return ias29RestatementEntryRepository.findAllByOrderByIdDesc().stream()
                .map(this::toResponse).toList();
    }

    /** Restates every ACTIVE fixed asset to restatementDate's price level. Skips an asset
     * whose restated figures haven't moved since they were last carried (no-op, no GL noise)
     * - see the class comment for why gross cost and accumulated depreciation are restated
     * independently rather than as one figure. Returns only the restatements actually
     * posted. */
    @Transactional
    public List<Ias29RestatementResponse> restateFixedAssets(LocalDate restatementDate, String performedBy) {
        List<Ias29RestatementResponse> results = new ArrayList<>();
        for (FixedAsset asset : fixedAssetRepository.findByStatus(FixedAssetStatus.ACTIVE)) {
            restateAsset(asset, restatementDate, performedBy).ifPresent(results::add);
        }
        log.info("IAS 29 restatement as of {} posted {} adjustment(s)", restatementDate, results.size());
        return results;
    }

    @Transactional
    public Ias29RestatementResponse reverseRestatement(Long entryId, String reason, String performedBy) {
        Ias29RestatementEntry entry = ias29RestatementEntryRepository.findById(entryId)
                .orElseThrow(() -> new IllegalArgumentException("Restatement entry not found: " + entryId));
        if (entry.isReversed()) {
            throw new IllegalStateException("Restatement entry " + entryId + " is already reversed");
        }

        JournalEntry reversalEntry = glPostingService.reverse(
                entry.getPostedJournalEntry(), LocalDate.now(), reason, performedBy);

        FixedAsset asset = entry.getFixedAsset();
        asset.setRestatedCost(entry.getPriorRestatedCost());
        asset.setRestatedAccumulatedDepreciation(entry.getPriorRestatedAccumulatedDepreciation());
        fixedAssetRepository.save(asset);

        entry.setReversed(true);
        entry.setReversalJournalEntry(reversalEntry);
        entry.setReversalReason(reason);
        entry.setReversedAt(LocalDateTime.now());
        Ias29RestatementEntry saved = ias29RestatementEntryRepository.save(entry);

        log.info("Reversed IAS 29 restatement #{} for asset {} - GL entry #{}",
                saved.getId(), asset.getAssetNumber(), reversalEntry.getEntryNumber());
        return toResponse(saved);
    }

    private Optional<Ias29RestatementResponse> restateAsset(FixedAsset asset, LocalDate restatementDate, String performedBy) {
        BigDecimal costFactor = generalPriceIndexService.getConversionFactor(asset.getAcquisitionDate(), restatementDate);
        BigDecimal newRestatedCost = asset.getAcquisitionCost().multiply(costFactor).setScale(4, java.math.RoundingMode.HALF_UP);

        BigDecimal newRestatedAccumulatedDepreciation = BigDecimal.ZERO;
        for (AssetDepreciation depreciation : assetDepreciationRepository.findByAssetOrderByPeriodDateDesc(asset)) {
            BigDecimal depreciationFactor = generalPriceIndexService.getConversionFactor(depreciation.getPeriodDate(), restatementDate);
            newRestatedAccumulatedDepreciation = newRestatedAccumulatedDepreciation
                    .add(depreciation.getAmount().multiply(depreciationFactor));
        }
        newRestatedAccumulatedDepreciation = newRestatedAccumulatedDepreciation.setScale(4, java.math.RoundingMode.HALF_UP);

        BigDecimal priorRestatedCost = asset.getRestatedCost() != null ? asset.getRestatedCost() : asset.getAcquisitionCost();
        BigDecimal priorRestatedAccumulatedDepreciation = asset.getRestatedAccumulatedDepreciation() != null
                ? asset.getRestatedAccumulatedDepreciation() : asset.getAccumulatedDepreciation();

        BigDecimal costDelta = newRestatedCost.subtract(priorRestatedCost);
        BigDecimal accumulatedDepreciationDelta = newRestatedAccumulatedDepreciation.subtract(priorRestatedAccumulatedDepreciation);
        BigDecimal netAdjustment = costDelta.subtract(accumulatedDepreciationDelta);

        if (costDelta.compareTo(BigDecimal.ZERO) == 0 && accumulatedDepreciationDelta.compareTo(BigDecimal.ZERO) == 0) {
            return Optional.empty();
        }

        JournalEntry entry = postToGeneralLedger(asset, restatementDate, costDelta, accumulatedDepreciationDelta, performedBy);

        asset.setRestatedCost(newRestatedCost);
        asset.setRestatedAccumulatedDepreciation(newRestatedAccumulatedDepreciation);
        fixedAssetRepository.save(asset);

        Ias29RestatementEntry saved = ias29RestatementEntryRepository.save(Ias29RestatementEntry.builder()
                .fixedAsset(asset)
                .restatementDate(restatementDate)
                .priorRestatedCost(priorRestatedCost)
                .newRestatedCost(newRestatedCost)
                .priorRestatedAccumulatedDepreciation(priorRestatedAccumulatedDepreciation)
                .newRestatedAccumulatedDepreciation(newRestatedAccumulatedDepreciation)
                .netAdjustment(netAdjustment)
                .postedJournalEntry(entry)
                .build());
        return Optional.of(toResponse(saved));
    }

    private JournalEntry postToGeneralLedger(FixedAsset asset, LocalDate restatementDate,
                                              BigDecimal costDelta, BigDecimal accumulatedDepreciationDelta, String performedBy) {
        Account fixedAssets = accountRepository.findByCode(FIXED_ASSETS_ACCOUNT_CODE)
                .orElseThrow(() -> new IllegalStateException("Chart of accounts is missing " + FIXED_ASSETS_ACCOUNT_CODE));
        Account accumulatedDepreciation = accountRepository.findByCode(ACCUMULATED_DEPRECIATION_ACCOUNT_CODE)
                .orElseThrow(() -> new IllegalStateException("Chart of accounts is missing " + ACCUMULATED_DEPRECIATION_ACCOUNT_CODE));
        Account restatementReserve = accountRepository.findByCode(RESTATEMENT_RESERVE_ACCOUNT_CODE)
                .orElseThrow(() -> new IllegalStateException("Chart of accounts is missing " + RESTATEMENT_RESERVE_ACCOUNT_CODE));

        Currency baseCurrency = currencyService.getBaseCurrency();
        String memo = "IAS 29 restatement of asset " + asset.getAssetNumber() + " (" + asset.getName() + ") as of " + restatementDate;
        List<ManualLineSpec> specs = new ArrayList<>();

        if (costDelta.compareTo(BigDecimal.ZERO) > 0) {
            specs.add(new ManualLineSpec(fixedAssets, costDelta, BigDecimal.ZERO, baseCurrency, BigDecimal.ONE, asset.getShop(), memo));
        } else if (costDelta.compareTo(BigDecimal.ZERO) < 0) {
            specs.add(new ManualLineSpec(fixedAssets, BigDecimal.ZERO, costDelta.negate(), baseCurrency, BigDecimal.ONE, asset.getShop(), memo));
        }

        if (accumulatedDepreciationDelta.compareTo(BigDecimal.ZERO) > 0) {
            specs.add(new ManualLineSpec(accumulatedDepreciation, BigDecimal.ZERO, accumulatedDepreciationDelta, baseCurrency, BigDecimal.ONE, asset.getShop(), memo));
        } else if (accumulatedDepreciationDelta.compareTo(BigDecimal.ZERO) < 0) {
            specs.add(new ManualLineSpec(accumulatedDepreciation, accumulatedDepreciationDelta.negate(), BigDecimal.ZERO, baseCurrency, BigDecimal.ONE, asset.getShop(), memo));
        }

        BigDecimal netAdjustment = costDelta.subtract(accumulatedDepreciationDelta);
        if (netAdjustment.compareTo(BigDecimal.ZERO) > 0) {
            specs.add(new ManualLineSpec(restatementReserve, BigDecimal.ZERO, netAdjustment, baseCurrency, BigDecimal.ONE, asset.getShop(), memo));
        } else if (netAdjustment.compareTo(BigDecimal.ZERO) < 0) {
            specs.add(new ManualLineSpec(restatementReserve, netAdjustment.negate(), BigDecimal.ZERO, baseCurrency, BigDecimal.ONE, asset.getShop(), memo));
        }

        return glPostingService.postManual(
                "IAS29-RESTATEMENT-" + asset.getId() + "-" + restatementDate,
                restatementDate, memo, GLSourceModule.SYSTEM, "FIXED_ASSET", asset.getId(), specs, performedBy);
    }

    private Ias29RestatementResponse toResponse(Ias29RestatementEntry entry) {
        return Ias29RestatementResponse.builder()
                .id(entry.getId())
                .fixedAssetId(entry.getFixedAsset().getId())
                .assetNumber(entry.getFixedAsset().getAssetNumber())
                .restatementDate(entry.getRestatementDate())
                .priorRestatedCost(entry.getPriorRestatedCost())
                .newRestatedCost(entry.getNewRestatedCost())
                .priorRestatedAccumulatedDepreciation(entry.getPriorRestatedAccumulatedDepreciation())
                .newRestatedAccumulatedDepreciation(entry.getNewRestatedAccumulatedDepreciation())
                .netAdjustment(entry.getNetAdjustment())
                .createdAt(entry.getCreatedAt())
                .postedJournalEntryId(entry.getPostedJournalEntry().getId())
                .postedJournalEntryNumber(entry.getPostedJournalEntry().getEntryNumber())
                .reversed(entry.isReversed())
                .reversalJournalEntryId(entry.getReversalJournalEntry() != null ? entry.getReversalJournalEntry().getId() : null)
                .reversalJournalEntryNumber(entry.getReversalJournalEntry() != null ? entry.getReversalJournalEntry().getEntryNumber() : null)
                .reversalReason(entry.getReversalReason())
                .reversedAt(entry.getReversedAt())
                .build();
    }
}
