package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.account.Account;
import com.pos_onlineshop.hybrid.account.AccountRepository;
import com.pos_onlineshop.hybrid.assetDepreciation.AssetDepreciation;
import com.pos_onlineshop.hybrid.assetDepreciation.AssetDepreciationRepository;
import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.dtos.AssetDepreciationResponse;
import com.pos_onlineshop.hybrid.enums.AccountType;
import com.pos_onlineshop.hybrid.enums.DebitCredit;
import com.pos_onlineshop.hybrid.enums.FixedAssetStatus;
import com.pos_onlineshop.hybrid.enums.GLSourceModule;
import com.pos_onlineshop.hybrid.fixedAsset.FixedAsset;
import com.pos_onlineshop.hybrid.fixedAsset.FixedAssetRepository;
import com.pos_onlineshop.hybrid.fixedAssetCategory.FixedAssetCategory;
import com.pos_onlineshop.hybrid.gl.ManualLineSpec;
import com.pos_onlineshop.hybrid.journalEntry.JournalEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssetDepreciationServiceTest {

    @Mock private FixedAssetRepository fixedAssetRepository;
    @Mock private AssetDepreciationRepository assetDepreciationRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private GLPostingService glPostingService;
    @Mock private CurrencyService currencyService;

    private AssetDepreciationService service;
    private Currency currency;
    private Account depreciationExpense;
    private Account accumulatedDepreciation;
    private final LocalDate periodDate = LocalDate.of(2026, 1, 31);

    @BeforeEach
    void setUp() {
        service = new AssetDepreciationService(fixedAssetRepository, assetDepreciationRepository,
                accountRepository, glPostingService, currencyService);

        currency = Currency.builder().id(1L).code("USD").build();
        depreciationExpense = Account.builder().id(1L).code("5400").name("Depreciation Expense")
                .accountType(AccountType.EXPENSE).normalBalance(DebitCredit.DEBIT).active(true).build();
        accumulatedDepreciation = Account.builder().id(2L).code("1590").name("Accumulated Depreciation")
                .accountType(AccountType.ASSET).normalBalance(DebitCredit.CREDIT).controlAccount(true).active(true).build();

        lenient().when(currencyService.getBaseCurrency()).thenReturn(currency);
        lenient().when(accountRepository.findByCode("5400")).thenReturn(Optional.of(depreciationExpense));
        lenient().when(accountRepository.findByCode("1590")).thenReturn(Optional.of(accumulatedDepreciation));
        lenient().when(fixedAssetRepository.save(any(FixedAsset.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(assetDepreciationRepository.save(any(AssetDepreciation.class))).thenAnswer(inv -> {
            AssetDepreciation d = inv.getArgument(0);
            if (d.getId() == null) d.setId(100L);
            return d;
        });
    }

    private FixedAsset asset(BigDecimal cost, BigDecimal residual, int usefulLifeMonths, BigDecimal accumulated) {
        return FixedAsset.builder().id(10L).assetNumber("FA-001").name("Delivery Van")
                .category(FixedAssetCategory.builder().id(1L).name("Vehicles").build())
                .acquisitionDate(LocalDate.of(2026, 1, 1)).acquisitionCost(cost).usefulLifeMonths(usefulLifeMonths)
                .residualValue(residual).accumulatedDepreciation(accumulated).status(FixedAssetStatus.ACTIVE).build();
    }

    @Test
    void postsStraightLineDepreciationForTheMonth() {
        // (24000 - 2400) / 48 = 450.00 per month
        FixedAsset asset = asset(new BigDecimal("24000.00"), new BigDecimal("2400.00"), 48, BigDecimal.ZERO);
        when(fixedAssetRepository.findByStatus(FixedAssetStatus.ACTIVE)).thenReturn(List.of(asset));
        when(assetDepreciationRepository.existsByAssetAndPeriodDate(asset, periodDate)).thenReturn(false);
        JournalEntry entry = JournalEntry.builder().id(500L).entryNumber(50L).build();
        ArgumentCaptor<List<ManualLineSpec>> captor = ArgumentCaptor.forClass(List.class);
        when(glPostingService.postManual(eq("DEPRECIATION-10-" + periodDate), eq(periodDate), anyString(),
                eq(GLSourceModule.SYSTEM), eq("FIXED_ASSET"), eq(10L), captor.capture(), eq("admin1")))
                .thenReturn(entry);

        List<AssetDepreciationResponse> results = service.runMonthlyDepreciation(periodDate, "admin1");

        assertEquals(1, results.size());
        assertEquals(0, new BigDecimal("450.0000").compareTo(results.get(0).getAmount()));
        assertEquals(0, new BigDecimal("450.0000").compareTo(asset.getAccumulatedDepreciation()));
        List<ManualLineSpec> specs = captor.getValue();
        assertTrue(specs.stream().anyMatch(s -> s.account() == depreciationExpense && s.debitAmount().compareTo(BigDecimal.ZERO) > 0));
        assertTrue(specs.stream().anyMatch(s -> s.account() == accumulatedDepreciation && s.creditAmount().compareTo(BigDecimal.ZERO) > 0));
    }

    @Test
    void skipsAnAssetAlreadyDepreciatedForThisPeriod() {
        FixedAsset asset = asset(new BigDecimal("24000.00"), new BigDecimal("2400.00"), 48, new BigDecimal("450.00"));
        when(fixedAssetRepository.findByStatus(FixedAssetStatus.ACTIVE)).thenReturn(List.of(asset));
        when(assetDepreciationRepository.existsByAssetAndPeriodDate(asset, periodDate)).thenReturn(true);

        List<AssetDepreciationResponse> results = service.runMonthlyDepreciation(periodDate, "admin1");

        assertTrue(results.isEmpty());
        verifyNoInteractions(glPostingService);
    }

    @Test
    void skipsAFullyDepreciatedAsset() {
        // Depreciable base is 21600 (24000 - 2400); already fully accumulated.
        FixedAsset asset = asset(new BigDecimal("24000.00"), new BigDecimal("2400.00"), 48, new BigDecimal("21600.00"));
        when(fixedAssetRepository.findByStatus(FixedAssetStatus.ACTIVE)).thenReturn(List.of(asset));
        when(assetDepreciationRepository.existsByAssetAndPeriodDate(asset, periodDate)).thenReturn(false);

        List<AssetDepreciationResponse> results = service.runMonthlyDepreciation(periodDate, "admin1");

        assertTrue(results.isEmpty());
        verifyNoInteractions(glPostingService);
        verify(assetDepreciationRepository, never()).save(any());
    }

    @Test
    void capsTheFinalPeriodsChargeAtTheRemainingDepreciableBase() {
        // Depreciable base 21600, monthly charge would be 450, but only 200 remains.
        FixedAsset asset = asset(new BigDecimal("24000.00"), new BigDecimal("2400.00"), 48, new BigDecimal("21400.00"));
        when(fixedAssetRepository.findByStatus(FixedAssetStatus.ACTIVE)).thenReturn(List.of(asset));
        when(assetDepreciationRepository.existsByAssetAndPeriodDate(asset, periodDate)).thenReturn(false);
        when(glPostingService.postManual(anyString(), eq(periodDate), anyString(), any(), anyString(), anyLong(), anyList(), anyString()))
                .thenReturn(JournalEntry.builder().id(500L).entryNumber(50L).build());

        List<AssetDepreciationResponse> results = service.runMonthlyDepreciation(periodDate, "admin1");

        assertEquals(1, results.size());
        assertEquals(0, new BigDecimal("200.0000").compareTo(results.get(0).getAmount()));
    }
}
