package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.account.Account;
import com.pos_onlineshop.hybrid.account.AccountRepository;
import com.pos_onlineshop.hybrid.assetDepreciation.AssetDepreciation;
import com.pos_onlineshop.hybrid.assetDepreciation.AssetDepreciationRepository;
import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.dtos.Ias29RestatementResponse;
import com.pos_onlineshop.hybrid.enums.AccountType;
import com.pos_onlineshop.hybrid.enums.DebitCredit;
import com.pos_onlineshop.hybrid.enums.FixedAssetStatus;
import com.pos_onlineshop.hybrid.fixedAsset.FixedAsset;
import com.pos_onlineshop.hybrid.fixedAsset.FixedAssetRepository;
import com.pos_onlineshop.hybrid.gl.ManualLineSpec;
import com.pos_onlineshop.hybrid.ias29Restatement.Ias29RestatementEntry;
import com.pos_onlineshop.hybrid.ias29Restatement.Ias29RestatementEntryRepository;
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
class Ias29RestatementServiceTest {

    @Mock private FixedAssetRepository fixedAssetRepository;
    @Mock private AssetDepreciationRepository assetDepreciationRepository;
    @Mock private Ias29RestatementEntryRepository ias29RestatementEntryRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private GLPostingService glPostingService;
    @Mock private GeneralPriceIndexService generalPriceIndexService;
    @Mock private CurrencyService currencyService;

    private Ias29RestatementService service;
    private Currency usd;
    private Account fixedAssets;
    private Account accumulatedDepreciation;
    private Account restatementReserve;
    private final LocalDate restatementDate = LocalDate.of(2026, 8, 31);
    private final LocalDate acquisitionDate = LocalDate.of(2024, 1, 1);

    @BeforeEach
    void setUp() {
        service = new Ias29RestatementService(fixedAssetRepository, assetDepreciationRepository,
                ias29RestatementEntryRepository, accountRepository, glPostingService, generalPriceIndexService, currencyService);

        usd = Currency.builder().id(1L).code("USD").build();
        fixedAssets = Account.builder().id(1L).code("1500").name("Fixed Assets")
                .accountType(AccountType.ASSET).normalBalance(DebitCredit.DEBIT).active(true).build();
        accumulatedDepreciation = Account.builder().id(2L).code("1590").name("Accumulated Depreciation")
                .accountType(AccountType.ASSET).normalBalance(DebitCredit.CREDIT).active(true).build();
        restatementReserve = Account.builder().id(3L).code("3910").name("IAS 29 Restatement Reserve")
                .accountType(AccountType.EQUITY).normalBalance(DebitCredit.CREDIT).active(true).build();

        lenient().when(currencyService.getBaseCurrency()).thenReturn(usd);
        lenient().when(accountRepository.findByCode("1500")).thenReturn(Optional.of(fixedAssets));
        lenient().when(accountRepository.findByCode("1590")).thenReturn(Optional.of(accumulatedDepreciation));
        lenient().when(accountRepository.findByCode("3910")).thenReturn(Optional.of(restatementReserve));
        lenient().when(fixedAssetRepository.save(any(FixedAsset.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(ias29RestatementEntryRepository.save(any(Ias29RestatementEntry.class))).thenAnswer(inv -> {
            Ias29RestatementEntry e = inv.getArgument(0);
            if (e.getId() == null) e.setId(500L);
            return e;
        });
        lenient().when(assetDepreciationRepository.findByAssetOrderByPeriodDateDesc(any(FixedAsset.class))).thenReturn(List.of());
    }

    private FixedAsset asset() {
        return FixedAsset.builder().id(10L).assetNumber("ASSET-1").name("Delivery Van")
                .acquisitionDate(acquisitionDate).acquisitionCost(new BigDecimal("1000.0000"))
                .accumulatedDepreciation(BigDecimal.ZERO).status(FixedAssetStatus.ACTIVE).build();
    }

    @Test
    void restatesGrossCostOnFirstRunWithNoDepreciationYet() {
        FixedAsset asset = asset();
        when(fixedAssetRepository.findByStatus(FixedAssetStatus.ACTIVE)).thenReturn(List.of(asset));
        when(generalPriceIndexService.getConversionFactor(acquisitionDate, restatementDate)).thenReturn(new BigDecimal("1.20000000"));
        JournalEntry entry = JournalEntry.builder().id(900L).entryNumber(90L).build();
        ArgumentCaptor<List<ManualLineSpec>> captor = ArgumentCaptor.forClass(List.class);
        when(glPostingService.postManual(eq("IAS29-RESTATEMENT-10-" + restatementDate), eq(restatementDate), anyString(),
                any(), eq("FIXED_ASSET"), eq(10L), captor.capture(), eq("admin1")))
                .thenReturn(entry);

        List<Ias29RestatementResponse> results = service.restateFixedAssets(restatementDate, "admin1");

        assertEquals(1, results.size());
        Ias29RestatementResponse response = results.get(0);
        assertEquals(0, new BigDecimal("1200.0000").compareTo(response.getNewRestatedCost()));
        assertEquals(0, new BigDecimal("200.0000").compareTo(response.getNetAdjustment()));
        assertEquals(0, new BigDecimal("1200.0000").compareTo(asset.getRestatedCost()));

        List<ManualLineSpec> specs = captor.getValue();
        BigDecimal totalDebits = specs.stream().map(ManualLineSpec::debitAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCredits = specs.stream().map(ManualLineSpec::creditAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, totalDebits.compareTo(totalCredits));
        ManualLineSpec costLine = specs.stream().filter(s -> s.account() == fixedAssets).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("200.0000").compareTo(costLine.debitAmount()));
        ManualLineSpec reserveLine = specs.stream().filter(s -> s.account() == restatementReserve).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("200.0000").compareTo(reserveLine.creditAmount()));
    }

    @Test
    void restatesAccumulatedDepreciationUsingEachChargesOwnPeriodDate() {
        FixedAsset asset = asset();
        asset.setAccumulatedDepreciation(new BigDecimal("300.0000"));
        LocalDate period1 = LocalDate.of(2024, 6, 30);
        LocalDate period2 = LocalDate.of(2024, 12, 31);
        AssetDepreciation charge1 = AssetDepreciation.builder().id(1L).asset(asset).periodDate(period1)
                .amount(new BigDecimal("150.0000")).accumulatedDepreciationAfter(new BigDecimal("150.0000")).build();
        AssetDepreciation charge2 = AssetDepreciation.builder().id(2L).asset(asset).periodDate(period2)
                .amount(new BigDecimal("150.0000")).accumulatedDepreciationAfter(new BigDecimal("300.0000")).build();
        when(fixedAssetRepository.findByStatus(FixedAssetStatus.ACTIVE)).thenReturn(List.of(asset));
        when(assetDepreciationRepository.findByAssetOrderByPeriodDateDesc(asset)).thenReturn(List.of(charge2, charge1));
        when(generalPriceIndexService.getConversionFactor(acquisitionDate, restatementDate)).thenReturn(new BigDecimal("1.20000000"));
        when(generalPriceIndexService.getConversionFactor(period1, restatementDate)).thenReturn(new BigDecimal("1.15000000"));
        when(generalPriceIndexService.getConversionFactor(period2, restatementDate)).thenReturn(new BigDecimal("1.10000000"));
        ArgumentCaptor<List<ManualLineSpec>> captor = ArgumentCaptor.forClass(List.class);
        when(glPostingService.postManual(anyString(), eq(restatementDate), anyString(), any(), anyString(), anyLong(), captor.capture(), anyString()))
                .thenReturn(JournalEntry.builder().id(901L).entryNumber(91L).build());

        List<Ias29RestatementResponse> results = service.restateFixedAssets(restatementDate, "admin1");

        // 150*1.15 + 150*1.10 = 172.50 + 165.00 = 337.50
        assertEquals(0, new BigDecimal("337.5000").compareTo(results.get(0).getNewRestatedAccumulatedDepreciation()));
        // costDelta 200.00 - accumDepDelta 37.50 = 162.50
        assertEquals(0, new BigDecimal("162.5000").compareTo(results.get(0).getNetAdjustment()));

        List<ManualLineSpec> specs = captor.getValue();
        BigDecimal totalDebits = specs.stream().map(ManualLineSpec::debitAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCredits = specs.stream().map(ManualLineSpec::creditAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, totalDebits.compareTo(totalCredits));
        ManualLineSpec accumDepLine = specs.stream().filter(s -> s.account() == accumulatedDepreciation).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("37.5000").compareTo(accumDepLine.creditAmount()));
    }

    @Test
    void skipsAnAssetWhoseIndexHasNotMovedSinceAcquisition() {
        FixedAsset asset = asset();
        when(fixedAssetRepository.findByStatus(FixedAssetStatus.ACTIVE)).thenReturn(List.of(asset));
        when(generalPriceIndexService.getConversionFactor(acquisitionDate, restatementDate)).thenReturn(BigDecimal.ONE);

        List<Ias29RestatementResponse> results = service.restateFixedAssets(restatementDate, "admin1");

        assertTrue(results.isEmpty());
        verifyNoInteractions(glPostingService);
        verify(fixedAssetRepository, never()).save(any());
    }

    @Test
    void secondRestatementDiffsAgainstThePriorRestatedValueNotTheHistoricalCost() {
        FixedAsset asset = asset();
        asset.setRestatedCost(new BigDecimal("1200.0000"));
        asset.setRestatedAccumulatedDepreciation(BigDecimal.ZERO);
        when(fixedAssetRepository.findByStatus(FixedAssetStatus.ACTIVE)).thenReturn(List.of(asset));
        when(generalPriceIndexService.getConversionFactor(acquisitionDate, restatementDate)).thenReturn(new BigDecimal("1.30000000"));
        ArgumentCaptor<List<ManualLineSpec>> captor = ArgumentCaptor.forClass(List.class);
        when(glPostingService.postManual(anyString(), eq(restatementDate), anyString(), any(), anyString(), anyLong(), captor.capture(), anyString()))
                .thenReturn(JournalEntry.builder().id(902L).entryNumber(92L).build());

        List<Ias29RestatementResponse> results = service.restateFixedAssets(restatementDate, "admin1");

        // newRestatedCost = 1000 * 1.30 = 1300.00; priorRestatedCost was already 1200.00 -> delta 100.00, not 300.00
        assertEquals(0, new BigDecimal("100.0000").compareTo(results.get(0).getNetAdjustment()));
    }

    @Test
    void reverseRestatementRestoresThePriorCarryingValuesAndPostsAContraEntry() {
        FixedAsset asset = asset();
        asset.setRestatedCost(new BigDecimal("1200.0000"));
        asset.setRestatedAccumulatedDepreciation(BigDecimal.ZERO);
        JournalEntry originalEntry = JournalEntry.builder().id(900L).entryNumber(90L).build();
        Ias29RestatementEntry restatementEntry = Ias29RestatementEntry.builder().id(500L).fixedAsset(asset)
                .restatementDate(restatementDate).priorRestatedCost(new BigDecimal("1000.0000"))
                .newRestatedCost(new BigDecimal("1200.0000")).priorRestatedAccumulatedDepreciation(BigDecimal.ZERO)
                .newRestatedAccumulatedDepreciation(BigDecimal.ZERO).netAdjustment(new BigDecimal("200.0000"))
                .postedJournalEntry(originalEntry).reversed(false).build();
        when(ias29RestatementEntryRepository.findById(500L)).thenReturn(Optional.of(restatementEntry));
        JournalEntry reversalEntry = JournalEntry.builder().id(910L).entryNumber(95L).build();
        when(glPostingService.reverse(originalEntry, LocalDate.now(), "Made in error", "admin1")).thenReturn(reversalEntry);

        Ias29RestatementResponse response = service.reverseRestatement(500L, "Made in error", "admin1");

        assertTrue(response.isReversed());
        assertEquals(0, new BigDecimal("1000.0000").compareTo(asset.getRestatedCost()));
        assertEquals(95L, response.getReversalJournalEntryNumber());
    }

    @Test
    void reverseRestatementRejectsAnAlreadyReversedEntry() {
        Ias29RestatementEntry restatementEntry = Ias29RestatementEntry.builder().id(500L).fixedAsset(asset())
                .reversed(true).build();
        when(ias29RestatementEntryRepository.findById(500L)).thenReturn(Optional.of(restatementEntry));

        assertThrows(IllegalStateException.class, () -> service.reverseRestatement(500L, "reason", "admin1"));
        verifyNoInteractions(glPostingService);
    }
}
