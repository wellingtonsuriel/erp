package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.account.Account;
import com.pos_onlineshop.hybrid.account.AccountRepository;
import com.pos_onlineshop.hybrid.dtos.AccountResponse;
import com.pos_onlineshop.hybrid.dtos.CreateAccountRequest;
import com.pos_onlineshop.hybrid.dtos.UpdateAccountRequest;
import com.pos_onlineshop.hybrid.enums.AccountType;
import com.pos_onlineshop.hybrid.enums.DebitCredit;
import com.pos_onlineshop.hybrid.journalLine.JournalLineRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock private AccountRepository accountRepository;
    @Mock private JournalLineRepository journalLineRepository;

    private AccountService service;

    @BeforeEach
    void setUp() {
        service = new AccountService(accountRepository, journalLineRepository);
    }

    private Account account(Long id, String code, AccountType type, boolean control) {
        return Account.builder().id(id).code(code).name(code + " account")
                .accountType(type).normalBalance(DebitCredit.DEBIT).controlAccount(control).active(true).build();
    }

    private CreateAccountRequest createRequest(String code) {
        CreateAccountRequest request = new CreateAccountRequest();
        request.setCode(code);
        request.setName("Test Account");
        request.setAccountType(AccountType.ASSET);
        request.setNormalBalance(DebitCredit.DEBIT);
        request.setActive(true);
        return request;
    }

    @Test
    void findAllWithActiveOnlyTrueUsesActiveRepositoryMethod() {
        when(accountRepository.findByActiveTrue()).thenReturn(List.of(account(1L, "1010", AccountType.ASSET, false)));

        List<AccountResponse> result = service.findAll(true);

        assertEquals(1, result.size());
        verify(accountRepository).findByActiveTrue();
        verify(accountRepository, never()).findAll();
    }

    @Test
    void findAllWithoutActiveOnlyReturnsEverything() {
        when(accountRepository.findAll()).thenReturn(List.of(account(1L, "1010", AccountType.ASSET, false)));

        List<AccountResponse> result = service.findAll(null);

        assertEquals(1, result.size());
        verify(accountRepository).findAll();
    }

    @Test
    void createRejectsDuplicateCode() {
        when(accountRepository.existsByCode("1010")).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> service.create(createRequest("1010")));
    }

    @Test
    void createSucceedsForNewCode() {
        when(accountRepository.existsByCode("9999")).thenReturn(false);
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        AccountResponse response = service.create(createRequest("9999"));

        assertEquals("9999", response.getCode());
        assertEquals(AccountType.ASSET, response.getAccountType());
    }

    @Test
    void createPropagatesCostOfGoodsSoldFlag() {
        when(accountRepository.existsByCode("5000")).thenReturn(false);
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateAccountRequest request = createRequest("5000");
        request.setAccountType(AccountType.EXPENSE);
        request.setCostOfGoodsSold(true);

        AccountResponse response = service.create(request);

        assertTrue(response.isCostOfGoodsSold());
    }

    @Test
    void updateRejectsAccountTypeChangeWhenJournalHistoryExists() {
        Account existing = account(1L, "1010", AccountType.ASSET, false);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(journalLineRepository.existsByAccount(existing)).thenReturn(true);

        UpdateAccountRequest request = new UpdateAccountRequest();
        request.setName("Renamed");
        request.setAccountType(AccountType.LIABILITY);
        request.setNormalBalance(DebitCredit.DEBIT);

        assertThrows(IllegalStateException.class, () -> service.update(1L, request));
    }

    @Test
    void updateAllowsAccountTypeChangeWhenNoJournalHistory() {
        Account existing = account(1L, "1010", AccountType.ASSET, false);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(journalLineRepository.existsByAccount(existing)).thenReturn(false);
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateAccountRequest request = new UpdateAccountRequest();
        request.setName("Renamed");
        request.setAccountType(AccountType.LIABILITY);
        request.setNormalBalance(DebitCredit.CREDIT);

        AccountResponse response = service.update(1L, request);

        assertEquals(AccountType.LIABILITY, response.getAccountType());
        assertEquals("Renamed", response.getName());
    }

    @Test
    void updateRejectsAnAccountBeingItsOwnParent() {
        Account existing = account(1L, "1010", AccountType.ASSET, false);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(existing));

        UpdateAccountRequest request = new UpdateAccountRequest();
        request.setName("Renamed");
        request.setAccountType(AccountType.ASSET);
        request.setNormalBalance(DebitCredit.DEBIT);
        request.setParentAccountId(1L);

        assertThrows(IllegalArgumentException.class, () -> service.update(1L, request));
    }

    @Test
    void activateSetsAccountActive() {
        Account existing = account(1L, "1010", AccountType.ASSET, false);
        existing.setActive(false);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        AccountResponse response = service.activate(1L);

        assertTrue(response.isActive());
    }

    @Test
    void deactivateSetsAccountInactive() {
        Account existing = account(1L, "1010", AccountType.ASSET, false);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        AccountResponse response = service.deactivate(1L);

        assertFalse(response.isActive());
    }

    @Test
    void findByIdThrowsWhenAccountDoesNotExist() {
        when(accountRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.findById(99L));
    }
}
