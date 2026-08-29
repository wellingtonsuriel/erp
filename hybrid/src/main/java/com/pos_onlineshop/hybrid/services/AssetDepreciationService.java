package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.account.Account;
import com.pos_onlineshop.hybrid.account.AccountRepository;
import com.pos_onlineshop.hybrid.assetDepreciation.AssetDepreciation;
import com.pos_onlineshop.hybrid.assetDepreciation.AssetDepreciationRepository;
import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.dtos.AssetDepreciationResponse;
import com.pos_onlineshop.hybrid.enums.FixedAssetStatus;
import com.pos_onlineshop.hybrid.enums.GLSourceModule;
import com.pos_onlineshop.hybrid.fixedAsset.FixedAsset;
import com.pos_onlineshop.hybrid.fixedAsset.FixedAssetRepository;
import com.pos_onlineshop.hybrid.gl.ManualLineSpec;
import com.pos_onlineshop.hybrid.journalEntry.JournalEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Monthly straight-line depreciation for every ACTIVE fixed asset. "Never depreciate the
 * same asset/period twice" is enforced two ways: AssetDepreciation carries a unique
 * (asset_id, period_date) database constraint (defense in depth, not just an in-memory
 * check), and this service checks existsByAssetAndPeriodDate before computing anything for
 * an asset/period pair, so a retried run for a period already processed is a silent no-op
 * rather than a duplicate posting or a thrown error.
 *
 * "Do not edit historical depreciation journals; corrections use reversals/adjustments" -
 * this service has no update/delete path for a posted AssetDepreciation at all; a correction
 * is a manual journal adjustment, same as every other posted GL-linked record here.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AssetDepreciationService {

    private static final String ACCUMULATED_DEPRECIATION_ACCOUNT_CODE = "1590";
    private static final String DEPRECIATION_EXPENSE_ACCOUNT_CODE = "5400";

    private final FixedAssetRepository fixedAssetRepository;
    private final AssetDepreciationRepository assetDepreciationRepository;
    private final AccountRepository accountRepository;
    private final GLPostingService glPostingService;
    private final CurrencyService currencyService;

    public List<AssetDepreciationResponse> findAll() {
        return assetDepreciationRepository.findAllByOrderByIdDesc().stream()
                .map(this::toResponse).toList();
    }

    /** Runs depreciation for every ACTIVE asset for periodDate (the last day of the
     * depreciated month). Returns only the charges actually posted - an asset already
     * depreciated for this period, or already fully depreciated, is silently skipped. */
    public List<AssetDepreciationResponse> runMonthlyDepreciation(LocalDate periodDate, String performedBy) {
        Currency baseCurrency = currencyService.getBaseCurrency();
        List<AssetDepreciationResponse> results = new ArrayList<>();

        for (FixedAsset asset : fixedAssetRepository.findByStatus(FixedAssetStatus.ACTIVE)) {
            if (assetDepreciationRepository.existsByAssetAndPeriodDate(asset, periodDate)) {
                continue;
            }
            if (asset.isFullyDepreciated()) {
                continue;
            }

            BigDecimal amount = calculateMonthlyDepreciation(asset);
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            Account accumulatedDepreciation = accountRepository.findByCode(ACCUMULATED_DEPRECIATION_ACCOUNT_CODE)
                    .orElseThrow(() -> new IllegalStateException("Chart of accounts is missing " + ACCUMULATED_DEPRECIATION_ACCOUNT_CODE));
            Account depreciationExpense = accountRepository.findByCode(DEPRECIATION_EXPENSE_ACCOUNT_CODE)
                    .orElseThrow(() -> new IllegalStateException("Chart of accounts is missing " + DEPRECIATION_EXPENSE_ACCOUNT_CODE));

            String memo = "Depreciation of asset " + asset.getAssetNumber() + " (" + asset.getName() + ") for " + periodDate;
            List<ManualLineSpec> specs = List.of(
                    new ManualLineSpec(depreciationExpense, amount, BigDecimal.ZERO, baseCurrency, BigDecimal.ONE, asset.getShop(), memo),
                    new ManualLineSpec(accumulatedDepreciation, BigDecimal.ZERO, amount, baseCurrency, BigDecimal.ONE, asset.getShop(), memo));

            JournalEntry entry = glPostingService.postManual(
                    "DEPRECIATION-" + asset.getId() + "-" + periodDate,
                    periodDate, memo, GLSourceModule.SYSTEM, "FIXED_ASSET", asset.getId(), specs, performedBy);

            asset.setAccumulatedDepreciation(asset.getAccumulatedDepreciation().add(amount));
            fixedAssetRepository.save(asset);

            AssetDepreciation saved = assetDepreciationRepository.save(AssetDepreciation.builder()
                    .asset(asset)
                    .periodDate(periodDate)
                    .amount(amount)
                    .accumulatedDepreciationAfter(asset.getAccumulatedDepreciation())
                    .postedJournalEntry(entry)
                    .build());
            results.add(toResponse(saved));
        }

        log.info("Depreciation run for {} posted {} charge(s)", periodDate, results.size());
        return results;
    }

    /** Straight-line only (see DepreciationMethod's comment): depreciableBase / usefulLifeMonths,
     * capped so an asset is never depreciated below its residual value even if this is its
     * final, possibly-partial period. */
    private BigDecimal calculateMonthlyDepreciation(FixedAsset asset) {
        BigDecimal monthly = asset.getDepreciableBase()
                .divide(BigDecimal.valueOf(asset.getUsefulLifeMonths()), 4, RoundingMode.HALF_UP);
        BigDecimal remaining = asset.getDepreciableBase().subtract(asset.getAccumulatedDepreciation());
        return monthly.min(remaining);
    }

    private AssetDepreciationResponse toResponse(AssetDepreciation depreciation) {
        return AssetDepreciationResponse.builder()
                .id(depreciation.getId())
                .assetId(depreciation.getAsset().getId())
                .assetNumber(depreciation.getAsset().getAssetNumber())
                .periodDate(depreciation.getPeriodDate())
                .amount(depreciation.getAmount())
                .accumulatedDepreciationAfter(depreciation.getAccumulatedDepreciationAfter())
                .createdAt(depreciation.getCreatedAt())
                .postedJournalEntryId(depreciation.getPostedJournalEntry().getId())
                .postedJournalEntryNumber(depreciation.getPostedJournalEntry().getEntryNumber())
                .build();
    }
}
