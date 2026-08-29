package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.account.Account;
import com.pos_onlineshop.hybrid.account.AccountRepository;
import com.pos_onlineshop.hybrid.assetDisposal.AssetDisposal;
import com.pos_onlineshop.hybrid.assetDisposal.AssetDisposalRepository;
import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.dtos.AssetDisposalResponse;
import com.pos_onlineshop.hybrid.dtos.DisposeAssetRequest;
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
import java.util.ArrayList;
import java.util.List;

/**
 * Disposes of a fixed asset: removes it and its accumulated depreciation from the books,
 * records the cash/other consideration received, and recognizes any gain or loss on 5950 -
 * see AssetDisposal's class comment. An asset can be disposed exactly once (FixedAsset.status
 * flips ACTIVE -> DISPOSED atomically with the posting, guarded by canBeDisposed()); disposing
 * an already-disposed asset is rejected, not silently re-posted.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AssetDisposalService {

    private static final String FIXED_ASSETS_ACCOUNT_CODE = "1500";
    private static final String ACCUMULATED_DEPRECIATION_ACCOUNT_CODE = "1590";
    private static final String CASH_ACCOUNT_CODE = "1010";
    private static final String GAIN_LOSS_ON_DISPOSAL_ACCOUNT_CODE = "5950";

    private final FixedAssetRepository fixedAssetRepository;
    private final AssetDisposalRepository assetDisposalRepository;
    private final AccountRepository accountRepository;
    private final GLPostingService glPostingService;
    private final CurrencyService currencyService;

    public List<AssetDisposalResponse> findAll() {
        return assetDisposalRepository.findAllByOrderByIdDesc().stream()
                .map(this::toResponse).toList();
    }

    public AssetDisposalResponse disposeAsset(Long assetId, DisposeAssetRequest request, String performedBy) {
        FixedAsset asset = fixedAssetRepository.findById(assetId)
                .orElseThrow(() -> new IllegalArgumentException("Fixed asset not found: " + assetId));
        if (asset.getStatus() != FixedAssetStatus.ACTIVE) {
            throw new IllegalStateException("Asset " + asset.getAssetNumber() + " cannot be disposed from status " + asset.getStatus());
        }

        BigDecimal netBookValue = asset.getNetBookValue();
        BigDecimal proceeds = request.getProceedsAmount();
        BigDecimal gainLoss = proceeds.subtract(netBookValue);

        JournalEntry entry = postDisposalToGeneralLedger(asset, proceeds, gainLoss, request.getDisposalDate(), performedBy);

        asset.setStatus(FixedAssetStatus.DISPOSED);
        fixedAssetRepository.save(asset);

        AssetDisposal saved = assetDisposalRepository.save(AssetDisposal.builder()
                .asset(asset)
                .disposalDate(request.getDisposalDate())
                .proceedsAmount(proceeds)
                .netBookValueAtDisposal(netBookValue)
                .gainLoss(gainLoss)
                .reason(request.getReason())
                .postedJournalEntry(entry)
                .build());

        log.info("Disposed asset {} (GL entry #{}, gain/loss {})", asset.getAssetNumber(), entry.getEntryNumber(), gainLoss);
        return toResponse(saved);
    }

    private JournalEntry postDisposalToGeneralLedger(FixedAsset asset, BigDecimal proceeds, BigDecimal gainLoss,
                                                      java.time.LocalDate disposalDate, String performedBy) {
        Currency baseCurrency = currencyService.getBaseCurrency();
        Account fixedAssets = accountRepository.findByCode(FIXED_ASSETS_ACCOUNT_CODE)
                .orElseThrow(() -> new IllegalStateException("Chart of accounts is missing " + FIXED_ASSETS_ACCOUNT_CODE));
        Account accumulatedDepreciation = accountRepository.findByCode(ACCUMULATED_DEPRECIATION_ACCOUNT_CODE)
                .orElseThrow(() -> new IllegalStateException("Chart of accounts is missing " + ACCUMULATED_DEPRECIATION_ACCOUNT_CODE));

        String memo = "Disposal of asset " + asset.getAssetNumber() + " (" + asset.getName() + ")";
        List<ManualLineSpec> specs = new ArrayList<>();
        // Clear accumulated depreciation (debit removes the contra-asset).
        if (asset.getAccumulatedDepreciation().compareTo(BigDecimal.ZERO) > 0) {
            specs.add(new ManualLineSpec(accumulatedDepreciation, asset.getAccumulatedDepreciation(), BigDecimal.ZERO,
                    baseCurrency, BigDecimal.ONE, asset.getShop(), memo));
        }
        // Record proceeds received, if any.
        if (proceeds.compareTo(BigDecimal.ZERO) > 0) {
            Account cash = accountRepository.findByCode(CASH_ACCOUNT_CODE)
                    .orElseThrow(() -> new IllegalStateException("Chart of accounts is missing " + CASH_ACCOUNT_CODE));
            specs.add(new ManualLineSpec(cash, proceeds, BigDecimal.ZERO, baseCurrency, BigDecimal.ONE, asset.getShop(), memo));
        }
        // Remove the asset at its full original cost.
        specs.add(new ManualLineSpec(fixedAssets, BigDecimal.ZERO, asset.getAcquisitionCost(), baseCurrency, BigDecimal.ONE, asset.getShop(), memo));

        if (gainLoss.compareTo(BigDecimal.ZERO) != 0) {
            Account gainLossAccount = accountRepository.findByCode(GAIN_LOSS_ON_DISPOSAL_ACCOUNT_CODE)
                    .orElseThrow(() -> new IllegalStateException("Chart of accounts is missing " + GAIN_LOSS_ON_DISPOSAL_ACCOUNT_CODE));
            if (gainLoss.compareTo(BigDecimal.ZERO) > 0) {
                specs.add(new ManualLineSpec(gainLossAccount, BigDecimal.ZERO, gainLoss, baseCurrency, BigDecimal.ONE, asset.getShop(), "Gain on disposal"));
            } else {
                specs.add(new ManualLineSpec(gainLossAccount, gainLoss.negate(), BigDecimal.ZERO, baseCurrency, BigDecimal.ONE, asset.getShop(), "Loss on disposal"));
            }
        }

        return glPostingService.postManual(
                "ASSET-DISPOSAL-" + asset.getId(), disposalDate, memo, GLSourceModule.SYSTEM, "FIXED_ASSET", asset.getId(), specs, performedBy);
    }

    private AssetDisposalResponse toResponse(AssetDisposal disposal) {
        return AssetDisposalResponse.builder()
                .id(disposal.getId())
                .assetId(disposal.getAsset().getId())
                .assetNumber(disposal.getAsset().getAssetNumber())
                .disposalDate(disposal.getDisposalDate())
                .proceedsAmount(disposal.getProceedsAmount())
                .netBookValueAtDisposal(disposal.getNetBookValueAtDisposal())
                .gainLoss(disposal.getGainLoss())
                .reason(disposal.getReason())
                .createdAt(disposal.getCreatedAt())
                .postedJournalEntryId(disposal.getPostedJournalEntry().getId())
                .postedJournalEntryNumber(disposal.getPostedJournalEntry().getEntryNumber())
                .build();
    }
}
