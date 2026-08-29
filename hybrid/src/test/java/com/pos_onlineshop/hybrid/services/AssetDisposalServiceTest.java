package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.account.Account;
import com.pos_onlineshop.hybrid.account.AccountRepository;
import com.pos_onlineshop.hybrid.assetDisposal.AssetDisposal;
import com.pos_onlineshop.hybrid.assetDisposal.AssetDisposalRepository;
import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.dtos.AssetDisposalResponse;
import com.pos_onlineshop.hybrid.dtos.DisposeAssetRequest;
import com.pos_onlineshop.hybrid.enums.AccountType;
import com.pos_onlineshop.hybrid.enums.DebitCredit;
import com.pos_onlineshop.hybrid.enums.FixedAssetStatus;
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
class AssetDisposalServiceTest {

    @Mock private FixedAssetRepository fixedAssetRepository;
    @Mock private AssetDisposalRepository assetDisposalRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private GLPostingService glPostingService;
    @Mock private CurrencyService currencyService;

    private AssetDisposalService service;
    private Currency currency;
    private Account fixedAssets;
    private Account accumulatedDepreciation;
    private Account cash;
    private Account gainLossOnDisposal;

    @BeforeEach
    void setUp() {
        service = new AssetDisposalService(fixedAssetRepository, assetDisposalRepository,
                accountRepository, glPostingService, currencyService);

        currency = Currency.builder().id(1L).code("USD").build();
        fixedAssets = Account.builder().id(1L).code("1500").name("Fixed Assets")
                .accountType(AccountType.ASSET).normalBalance(DebitCredit.DEBIT).controlAccount(true).active(true).build();
        accumulatedDepreciation = Account.builder().id(2L).code("1590").name("Accumulated Depreciation")
                .accountType(AccountType.ASSET).normalBalance(DebitCredit.CREDIT).controlAccount(true).active(true).build();
        cash = Account.builder().id(3L).code("1010").name("Cash on Hand")
                .accountType(AccountType.ASSET).normalBalance(DebitCredit.DEBIT).active(true).build();
        gainLossOnDisposal = Account.builder().id(4L).code("5950").name("Gain / Loss on Disposal of Assets")
                .accountType(AccountType.EXPENSE).normalBalance(DebitCredit.DEBIT).active(true).build();

        lenient().when(currencyService.getBaseCurrency()).thenReturn(currency);
        lenient().when(accountRepository.findByCode("1500")).thenReturn(Optional.of(fixedAssets));
        lenient().when(accountRepository.findByCode("1590")).thenReturn(Optional.of(accumulatedDepreciation));
        lenient().when(accountRepository.findByCode("1010")).thenReturn(Optional.of(cash));
        lenient().when(accountRepository.findByCode("5950")).thenReturn(Optional.of(gainLossOnDisposal));
        lenient().when(fixedAssetRepository.save(any(FixedAsset.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(assetDisposalRepository.save(any(AssetDisposal.class))).thenAnswer(inv -> {
            AssetDisposal d = inv.getArgument(0);
            if (d.getId() == null) d.setId(200L);
            return d;
        });
    }

    private FixedAsset asset(BigDecimal cost, BigDecimal accumulated) {
        return FixedAsset.builder().id(10L).assetNumber("FA-001").name("Delivery Van")
                .category(FixedAssetCategory.builder().id(1L).name("Vehicles").build())
                .acquisitionDate(LocalDate.of(2026, 1, 1)).acquisitionCost(cost).usefulLifeMonths(48)
                .residualValue(BigDecimal.ZERO).accumulatedDepreciation(accumulated).status(FixedAssetStatus.ACTIVE).build();
    }

    private DisposeAssetRequest request(BigDecimal proceeds) {
        DisposeAssetRequest request = new DisposeAssetRequest();
        request.setDisposalDate(LocalDate.of(2026, 6, 1));
        request.setProceedsAmount(proceeds);
        request.setReason("End of useful life");
        return request;
    }

    @Test
    void disposalRejectsAnAlreadyDisposedAsset() {
        FixedAsset asset = asset(new BigDecimal("24000.00"), new BigDecimal("10000.00"));
        asset.setStatus(FixedAssetStatus.DISPOSED);
        when(fixedAssetRepository.findById(10L)).thenReturn(Optional.of(asset));

        assertThrows(IllegalStateException.class, () -> service.disposeAsset(10L, request(BigDecimal.ZERO), "admin1"));
        verifyNoInteractions(glPostingService);
    }

    @Test
    void proceedsExceedingNetBookValueRecognizeAGain() {
        // Net book value = 24000 - 20000 = 4000; sold for 5000 -> 1000 gain.
        FixedAsset asset = asset(new BigDecimal("24000.00"), new BigDecimal("20000.00"));
        when(fixedAssetRepository.findById(10L)).thenReturn(Optional.of(asset));
        JournalEntry entry = JournalEntry.builder().id(600L).entryNumber(60L).build();
        ArgumentCaptor<List<ManualLineSpec>> captor = ArgumentCaptor.forClass(List.class);
        when(glPostingService.postManual(eq("ASSET-DISPOSAL-10"), eq(LocalDate.of(2026, 6, 1)), anyString(),
                any(), eq("FIXED_ASSET"), eq(10L), captor.capture(), eq("admin1"))).thenReturn(entry);

        AssetDisposalResponse response = service.disposeAsset(10L, request(new BigDecimal("5000.00")), "admin1");

        assertEquals(0, new BigDecimal("1000.00").compareTo(response.getGainLoss()));
        assertEquals(FixedAssetStatus.DISPOSED, asset.getStatus());
        List<ManualLineSpec> specs = captor.getValue();
        ManualLineSpec gainLine = specs.stream().filter(s -> s.account() == gainLossOnDisposal).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("1000.00").compareTo(gainLine.creditAmount()));
        // Debits: accumDep(20000) + cash(5000) = 25000; Credits: fixedAssets(24000) + gain(1000) = 25000
        BigDecimal totalDebits = specs.stream().map(ManualLineSpec::debitAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCredits = specs.stream().map(ManualLineSpec::creditAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, totalDebits.compareTo(totalCredits));
    }

    @Test
    void proceedsBelowNetBookValueRecognizeALoss() {
        // Net book value = 24000 - 20000 = 4000; sold for 1000 -> 3000 loss.
        FixedAsset asset = asset(new BigDecimal("24000.00"), new BigDecimal("20000.00"));
        when(fixedAssetRepository.findById(10L)).thenReturn(Optional.of(asset));
        when(glPostingService.postManual(anyString(), any(LocalDate.class), anyString(), any(), anyString(), anyLong(), anyList(), anyString()))
                .thenReturn(JournalEntry.builder().id(600L).entryNumber(60L).build());

        AssetDisposalResponse response = service.disposeAsset(10L, request(new BigDecimal("1000.00")), "admin1");

        assertEquals(0, new BigDecimal("-3000.00").compareTo(response.getGainLoss()));
    }

    @Test
    void scrappingAnAssetWithNoProceedsRecognizesTheFullNetBookValueAsALoss() {
        FixedAsset asset = asset(new BigDecimal("24000.00"), new BigDecimal("20000.00"));
        when(fixedAssetRepository.findById(10L)).thenReturn(Optional.of(asset));
        JournalEntry entry = JournalEntry.builder().id(600L).entryNumber(60L).build();
        ArgumentCaptor<List<ManualLineSpec>> captor = ArgumentCaptor.forClass(List.class);
        when(glPostingService.postManual(anyString(), any(LocalDate.class), anyString(), any(), anyString(), anyLong(), captor.capture(), anyString()))
                .thenReturn(entry);

        AssetDisposalResponse response = service.disposeAsset(10L, request(BigDecimal.ZERO), "admin1");

        assertEquals(0, new BigDecimal("-4000.00").compareTo(response.getGainLoss()));
        assertTrue(captor.getValue().stream().noneMatch(s -> s.account() == cash));
    }
}
