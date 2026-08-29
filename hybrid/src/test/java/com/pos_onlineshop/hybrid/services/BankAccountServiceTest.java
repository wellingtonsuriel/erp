package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.account.Account;
import com.pos_onlineshop.hybrid.account.AccountRepository;
import com.pos_onlineshop.hybrid.bankAccount.BankAccount;
import com.pos_onlineshop.hybrid.bankAccount.BankAccountRepository;
import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.currency.CurrencyRepository;
import com.pos_onlineshop.hybrid.dtos.BankAccountResponse;
import com.pos_onlineshop.hybrid.dtos.CreateBankAccountRequest;
import com.pos_onlineshop.hybrid.dtos.OpeningBalanceResponse;
import com.pos_onlineshop.hybrid.enums.AccountType;
import com.pos_onlineshop.hybrid.enums.CashBankAccountType;
import com.pos_onlineshop.hybrid.enums.DebitCredit;
import com.pos_onlineshop.hybrid.shop.ShopRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BankAccountServiceTest {

    @Mock private BankAccountRepository bankAccountRepository;
    @Mock private CurrencyRepository currencyRepository;
    @Mock private ShopRepository shopRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private OpeningBalanceService openingBalanceService;

    private BankAccountService service;
    private Currency usd;
    private Account bankGlAccount;

    @BeforeEach
    void setUp() {
        service = new BankAccountService(bankAccountRepository, currencyRepository, shopRepository, accountRepository, openingBalanceService);

        usd = Currency.builder().id(1L).code("USD").build();
        bankGlAccount = Account.builder().id(1L).code("1030").name("Bank")
                .accountType(AccountType.ASSET).normalBalance(DebitCredit.DEBIT).active(true).build();

        lenient().when(currencyRepository.findById(1L)).thenReturn(Optional.of(usd));
        lenient().when(accountRepository.findByCode("1030")).thenReturn(Optional.of(bankGlAccount));
        lenient().when(bankAccountRepository.save(any(BankAccount.class))).thenAnswer(inv -> {
            BankAccount b = inv.getArgument(0);
            if (b.getId() == null) b.setId(10L);
            return b;
        });
    }

    private CreateBankAccountRequest request() {
        CreateBankAccountRequest request = new CreateBankAccountRequest();
        request.setAccountName("CBZ Main Account");
        request.setAccountType(CashBankAccountType.BANK);
        request.setCurrencyId(1L);
        request.setCreatedByUserId(1L);
        return request;
    }

    @Test
    void createRejectsADuplicateAccountName() {
        when(bankAccountRepository.existsByAccountName("CBZ Main Account")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> service.create(request()));
    }

    @Test
    void createDefaultsTheGlAccountCodeByType() {
        when(bankAccountRepository.existsByAccountName(anyString())).thenReturn(false);

        BankAccountResponse response = service.create(request());

        assertEquals("1030", response.getGlAccountCode());
        assertEquals(0, BigDecimal.ZERO.compareTo(response.getCurrentBalance()));
        verifyNoInteractions(openingBalanceService);
    }

    @Test
    void createRequiresAnOpeningBalanceDateWhenAnOpeningBalanceIsGiven() {
        when(bankAccountRepository.existsByAccountName(anyString())).thenReturn(false);
        CreateBankAccountRequest request = request();
        request.setOpeningBalance(new BigDecimal("500.00"));

        assertThrows(IllegalArgumentException.class, () -> service.create(request));
    }

    @Test
    void createSeedsAPositiveOpeningBalanceThroughOpeningBalanceService() {
        when(bankAccountRepository.existsByAccountName(anyString())).thenReturn(false);
        when(openingBalanceService.createOpeningBalance(any())).thenReturn(OpeningBalanceResponse.builder().id(1L).build());
        CreateBankAccountRequest request = request();
        request.setOpeningBalance(new BigDecimal("500.00"));
        request.setOpeningBalanceDate(LocalDate.of(2026, 8, 1));

        BankAccountResponse response = service.create(request);

        assertEquals(0, new BigDecimal("500.00").compareTo(response.getCurrentBalance()));
        verify(openingBalanceService).createOpeningBalance(any());
    }

    @Test
    void deactivateMarksTheAccountInactive() {
        BankAccount account = BankAccount.builder().id(10L).accountName("CBZ Main Account")
                .accountType(CashBankAccountType.BANK).glAccountCode("1030").currency(usd).active(true).build();
        when(bankAccountRepository.findById(10L)).thenReturn(Optional.of(account));

        BankAccountResponse response = service.deactivate(10L);

        assertFalse(response.isActive());
    }
}
