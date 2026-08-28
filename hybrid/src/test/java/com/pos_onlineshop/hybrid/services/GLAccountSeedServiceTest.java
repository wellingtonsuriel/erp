package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.account.Account;
import com.pos_onlineshop.hybrid.account.AccountRepository;
import com.pos_onlineshop.hybrid.enums.AccountType;
import com.pos_onlineshop.hybrid.enums.DebitCredit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GLAccountSeedServiceTest {

    @Mock private AccountRepository accountRepository;

    private GLAccountSeedService service;

    @BeforeEach
    void setUp() {
        service = new GLAccountSeedService(accountRepository);
    }

    @Test
    void seedCreatesEveryStarterAccountOnAnEmptyChart() {
        when(accountRepository.findByCode(any())).thenReturn(Optional.empty());

        service.seed();

        verify(accountRepository, times(20)).save(any(Account.class));
    }

    @Test
    void seedMarksAccount5000AsCostOfGoodsSoldOnCreation() {
        when(accountRepository.findByCode(any())).thenReturn(Optional.empty());

        service.seed();

        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository, atLeastOnce()).save(captor.capture());
        Account cogsAccount = captor.getAllValues().stream()
                .filter(a -> "5000".equals(a.getCode())).findFirst().orElseThrow();
        assertTrue(cogsAccount.isCostOfGoodsSold());

        Account otherExpense = captor.getAllValues().stream()
                .filter(a -> "5300".equals(a.getCode())).findFirst().orElseThrow();
        assertFalse(otherExpense.isCostOfGoodsSold());
    }

    @Test
    void seedSyncsCostOfGoodsSoldFlagOnAnAlreadyExistingAccount5000() {
        Account existingCogsAccount = Account.builder().id(99L).code("5000").name("Cost of Goods Sold")
                .accountType(AccountType.EXPENSE).normalBalance(DebitCredit.DEBIT)
                .costOfGoodsSold(false) // deployed before the flag existed
                .active(true).build();

        when(accountRepository.findByCode(any())).thenAnswer(inv -> {
            String code = inv.getArgument(0);
            return "5000".equals(code) ? Optional.of(existingCogsAccount) : Optional.empty();
        });

        service.seed();

        assertTrue(existingCogsAccount.isCostOfGoodsSold());
        verify(accountRepository).save(existingCogsAccount);
    }

    @Test
    void seedDoesNotResaveAnAlreadyCorrectlyClassifiedExistingAccount() {
        Account existingNonCogs = Account.builder().id(50L).code("5300").name("Operating Expenses")
                .accountType(AccountType.EXPENSE).normalBalance(DebitCredit.DEBIT)
                .costOfGoodsSold(false).active(true).build();

        when(accountRepository.findByCode(any())).thenAnswer(inv -> {
            String code = inv.getArgument(0);
            return "5300".equals(code) ? Optional.of(existingNonCogs) : Optional.empty();
        });

        service.seed();

        verify(accountRepository, never()).save(existingNonCogs);
    }
}
