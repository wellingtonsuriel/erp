package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.account.Account;
import com.pos_onlineshop.hybrid.account.AccountRepository;
import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.dtos.CreateFixedAssetRequest;
import com.pos_onlineshop.hybrid.dtos.FixedAssetResponse;
import com.pos_onlineshop.hybrid.enums.DepreciationMethod;
import com.pos_onlineshop.hybrid.enums.GLSourceModule;
import com.pos_onlineshop.hybrid.fixedAsset.FixedAsset;
import com.pos_onlineshop.hybrid.fixedAsset.FixedAssetRepository;
import com.pos_onlineshop.hybrid.fixedAssetCategory.FixedAssetCategory;
import com.pos_onlineshop.hybrid.fixedAssetCategory.FixedAssetCategoryRepository;
import com.pos_onlineshop.hybrid.gl.ManualLineSpec;
import com.pos_onlineshop.hybrid.journalEntry.JournalEntry;
import com.pos_onlineshop.hybrid.shop.Shop;
import com.pos_onlineshop.hybrid.shop.ShopRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Fixed asset registration. Registering an asset posts its acquisition to the GL in the same
 * transaction (Dr 1500 Fixed Assets / Cr 2100 Accounts Payable) - the same "one atomic action
 * creates the subledger row and posts GL" pattern POSService/OrderService already use for a
 * sale, rather than a separate, second, independently-failable posting step. Acquisition is
 * always funded on account (2100 AP): a cash/bank-funded acquisition isn't modeled as a
 * distinct option in this slice - documented as a known limitation, workable today via a
 * manual journal to reclassify AP to Cash/Bank if needed.
 *
 * See AssetDepreciationService for monthly depreciation and AssetDisposalService for
 * disposal - both separate services, since each has a genuinely distinct posting mechanism
 * and lifecycle, matching how OpeningBalanceService/AccrualService/FxRevaluationService are
 * kept separate rather than folded into one do-everything service.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FixedAssetService {

    private static final String FIXED_ASSETS_ACCOUNT_CODE = "1500";
    private static final String ACCOUNTS_PAYABLE_ACCOUNT_CODE = "2100";

    private final FixedAssetRepository fixedAssetRepository;
    private final FixedAssetCategoryRepository fixedAssetCategoryRepository;
    private final ShopRepository shopRepository;
    private final AccountRepository accountRepository;
    private final GLPostingService glPostingService;
    private final CurrencyService currencyService;

    @Transactional(readOnly = true)
    public List<FixedAssetResponse> findAll() {
        return fixedAssetRepository.findAllByOrderByIdDesc().stream()
                .map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public FixedAssetResponse findById(Long id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    public FixedAssetResponse registerAsset(CreateFixedAssetRequest request) {
        if (fixedAssetRepository.existsByAssetNumber(request.getAssetNumber())) {
            throw new IllegalArgumentException("An asset with number " + request.getAssetNumber() + " already exists");
        }
        FixedAssetCategory category = fixedAssetCategoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new IllegalArgumentException("Category not found: " + request.getCategoryId()));
        Shop shop = null;
        if (request.getShopId() != null) {
            shop = shopRepository.findById(request.getShopId())
                    .orElseThrow(() -> new IllegalArgumentException("Shop not found: " + request.getShopId()));
        }

        FixedAsset asset = FixedAsset.builder()
                .assetNumber(request.getAssetNumber())
                .name(request.getName())
                .category(category)
                .shop(shop)
                .acquisitionDate(request.getAcquisitionDate())
                .acquisitionCost(request.getAcquisitionCost())
                .usefulLifeMonths(request.getUsefulLifeMonths())
                .residualValue(request.getResidualValue() != null ? request.getResidualValue() : BigDecimal.ZERO)
                .depreciationMethod(request.getDepreciationMethod() != null ? request.getDepreciationMethod()
                        : DepreciationMethod.STRAIGHT_LINE)
                .build();
        if (asset.getResidualValue().compareTo(asset.getAcquisitionCost()) > 0) {
            throw new IllegalArgumentException("Residual value cannot exceed acquisition cost");
        }

        FixedAsset saved = fixedAssetRepository.save(asset);

        JournalEntry entry = postAcquisitionToGeneralLedger(saved);
        saved.setAcquisitionJournalEntry(entry);
        saved = fixedAssetRepository.save(saved);

        log.info("Registered fixed asset {} ({}) - GL entry #{}",
                saved.getAssetNumber(), saved.getName(), entry.getEntryNumber());
        return toResponse(saved);
    }

    private JournalEntry postAcquisitionToGeneralLedger(FixedAsset asset) {
        Currency baseCurrency = currencyService.getBaseCurrency();
        Account fixedAssets = accountRepository.findByCode(FIXED_ASSETS_ACCOUNT_CODE)
                .orElseThrow(() -> new IllegalStateException("Chart of accounts is missing " + FIXED_ASSETS_ACCOUNT_CODE));
        Account accountsPayable = accountRepository.findByCode(ACCOUNTS_PAYABLE_ACCOUNT_CODE)
                .orElseThrow(() -> new IllegalStateException("Chart of accounts is missing " + ACCOUNTS_PAYABLE_ACCOUNT_CODE));

        String memo = "Acquisition of asset " + asset.getAssetNumber() + " (" + asset.getName() + ")";
        List<ManualLineSpec> specs = List.of(
                new ManualLineSpec(fixedAssets, asset.getAcquisitionCost(), BigDecimal.ZERO, baseCurrency, BigDecimal.ONE, asset.getShop(), memo),
                new ManualLineSpec(accountsPayable, BigDecimal.ZERO, asset.getAcquisitionCost(), baseCurrency, BigDecimal.ONE, asset.getShop(), memo));

        return glPostingService.postManual(
                "ASSET-ACQUISITION-" + asset.getAssetNumber(),
                asset.getAcquisitionDate(), memo, GLSourceModule.SYSTEM, "FIXED_ASSET", asset.getId(), specs, "system");
    }

    private FixedAsset findOrThrow(Long id) {
        return fixedAssetRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Fixed asset not found: " + id));
    }

    private FixedAssetResponse toResponse(FixedAsset asset) {
        return FixedAssetResponse.builder()
                .id(asset.getId())
                .assetNumber(asset.getAssetNumber())
                .name(asset.getName())
                .categoryId(asset.getCategory().getId())
                .categoryName(asset.getCategory().getName())
                .shopId(asset.getShop() != null ? asset.getShop().getId() : null)
                .shopName(asset.getShop() != null ? asset.getShop().getName() : null)
                .acquisitionDate(asset.getAcquisitionDate())
                .acquisitionCost(asset.getAcquisitionCost())
                .usefulLifeMonths(asset.getUsefulLifeMonths())
                .residualValue(asset.getResidualValue())
                .depreciationMethod(asset.getDepreciationMethod().name())
                .accumulatedDepreciation(asset.getAccumulatedDepreciation())
                .netBookValue(asset.getNetBookValue())
                .status(asset.getStatus().name())
                .createdAt(asset.getCreatedAt())
                .acquisitionJournalEntryId(asset.getAcquisitionJournalEntry() != null ? asset.getAcquisitionJournalEntry().getId() : null)
                .acquisitionJournalEntryNumber(asset.getAcquisitionJournalEntry() != null ? asset.getAcquisitionJournalEntry().getEntryNumber() : null)
                .build();
    }
}
