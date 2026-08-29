package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.account.Account;
import com.pos_onlineshop.hybrid.account.AccountRepository;
import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.dtos.CreateFixedAssetRequest;
import com.pos_onlineshop.hybrid.dtos.FixedAssetResponse;
import com.pos_onlineshop.hybrid.enums.AccountType;
import com.pos_onlineshop.hybrid.enums.DebitCredit;
import com.pos_onlineshop.hybrid.enums.GLSourceModule;
import com.pos_onlineshop.hybrid.fixedAsset.FixedAsset;
import com.pos_onlineshop.hybrid.fixedAsset.FixedAssetRepository;
import com.pos_onlineshop.hybrid.fixedAssetCategory.FixedAssetCategory;
import com.pos_onlineshop.hybrid.fixedAssetCategory.FixedAssetCategoryRepository;
import com.pos_onlineshop.hybrid.gl.ManualLineSpec;
import com.pos_onlineshop.hybrid.journalEntry.JournalEntry;
import com.pos_onlineshop.hybrid.shop.ShopRepository;
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
class FixedAssetServiceTest {

    @Mock private FixedAssetRepository fixedAssetRepository;
    @Mock private FixedAssetCategoryRepository fixedAssetCategoryRepository;
    @Mock private ShopRepository shopRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private GLPostingService glPostingService;
    @Mock private CurrencyService currencyService;

    private FixedAssetService service;
    private Currency currency;
    private FixedAssetCategory category;
    private Account fixedAssets;
    private Account accountsPayable;

    @BeforeEach
    void setUp() {
        service = new FixedAssetService(fixedAssetRepository, fixedAssetCategoryRepository,
                shopRepository, accountRepository, glPostingService, currencyService);

        currency = Currency.builder().id(1L).code("USD").build();
        category = FixedAssetCategory.builder().id(1L).name("Vehicles").active(true).build();
        fixedAssets = Account.builder().id(1L).code("1500").name("Fixed Assets")
                .accountType(AccountType.ASSET).normalBalance(DebitCredit.DEBIT).controlAccount(true).active(true).build();
        accountsPayable = Account.builder().id(2L).code("2100").name("Accounts Payable")
                .accountType(AccountType.LIABILITY).normalBalance(DebitCredit.CREDIT).controlAccount(true).active(true).build();

        lenient().when(currencyService.getBaseCurrency()).thenReturn(currency);
        lenient().when(accountRepository.findByCode("1500")).thenReturn(Optional.of(fixedAssets));
        lenient().when(accountRepository.findByCode("2100")).thenReturn(Optional.of(accountsPayable));
        lenient().when(fixedAssetCategoryRepository.findById(1L)).thenReturn(Optional.of(category));
        lenient().when(fixedAssetRepository.save(any(FixedAsset.class))).thenAnswer(inv -> {
            FixedAsset a = inv.getArgument(0);
            if (a.getId() == null) a.setId(10L);
            return a;
        });
    }

    private CreateFixedAssetRequest request() {
        CreateFixedAssetRequest request = new CreateFixedAssetRequest();
        request.setAssetNumber("FA-001");
        request.setName("Delivery Van");
        request.setCategoryId(1L);
        request.setAcquisitionDate(LocalDate.of(2026, 1, 1));
        request.setAcquisitionCost(new BigDecimal("24000.00"));
        request.setUsefulLifeMonths(48);
        request.setResidualValue(new BigDecimal("2400.00"));
        return request;
    }

    @Test
    void registerAssetRejectsADuplicateAssetNumber() {
        when(fixedAssetRepository.existsByAssetNumber("FA-001")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> service.registerAsset(request()));
        verifyNoInteractions(glPostingService);
    }

    @Test
    void registerAssetRejectsResidualValueExceedingCost() {
        when(fixedAssetRepository.existsByAssetNumber("FA-001")).thenReturn(false);
        CreateFixedAssetRequest request = request();
        request.setResidualValue(new BigDecimal("99999.00"));

        assertThrows(IllegalArgumentException.class, () -> service.registerAsset(request));
        verifyNoInteractions(glPostingService);
    }

    @Test
    void registerAssetPostsAcquisitionToTheGeneralLedger() {
        when(fixedAssetRepository.existsByAssetNumber("FA-001")).thenReturn(false);
        JournalEntry entry = JournalEntry.builder().id(500L).entryNumber(50L).build();
        ArgumentCaptor<List<ManualLineSpec>> captor = ArgumentCaptor.forClass(List.class);
        when(glPostingService.postManual(eq("ASSET-ACQUISITION-FA-001"), eq(LocalDate.of(2026, 1, 1)), anyString(),
                eq(GLSourceModule.SYSTEM), eq("FIXED_ASSET"), eq(10L), captor.capture(), eq("system")))
                .thenReturn(entry);

        FixedAssetResponse response = service.registerAsset(request());

        assertEquals("ACTIVE", response.getStatus());
        assertEquals(0, new BigDecimal("24000.00").compareTo(response.getNetBookValue()));
        assertEquals(50L, response.getAcquisitionJournalEntryNumber());
        List<ManualLineSpec> specs = captor.getValue();
        assertEquals(2, specs.size());
        assertTrue(specs.stream().anyMatch(s -> s.account() == fixedAssets && s.debitAmount().compareTo(BigDecimal.ZERO) > 0));
        assertTrue(specs.stream().anyMatch(s -> s.account() == accountsPayable && s.creditAmount().compareTo(BigDecimal.ZERO) > 0));
    }
}
