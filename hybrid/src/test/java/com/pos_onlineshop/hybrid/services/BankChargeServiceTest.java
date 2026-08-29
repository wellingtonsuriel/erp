package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.account.Account;
import com.pos_onlineshop.hybrid.account.AccountRepository;
import com.pos_onlineshop.hybrid.bankAccount.BankAccount;
import com.pos_onlineshop.hybrid.bankAccount.BankAccountRepository;
import com.pos_onlineshop.hybrid.bankCharge.BankCharge;
import com.pos_onlineshop.hybrid.bankCharge.BankChargeRepository;
import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.dtos.BankChargeResponse;
import com.pos_onlineshop.hybrid.dtos.CreateBankChargeRequest;
import com.pos_onlineshop.hybrid.enums.AccountType;
import com.pos_onlineshop.hybrid.enums.CashBankAccountType;
import com.pos_onlineshop.hybrid.enums.DebitCredit;
import com.pos_onlineshop.hybrid.gl.ManualLineSpec;
import com.pos_onlineshop.hybrid.journalEntry.JournalEntry;
import com.pos_onlineshop.hybrid.userAccount.UserAccount;
import com.pos_onlineshop.hybrid.userAccount.UserAccountRepository;
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
class BankChargeServiceTest {

    @Mock private BankChargeRepository bankChargeRepository;
    @Mock private BankAccountRepository bankAccountRepository;
    @Mock private UserAccountRepository userAccountRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private GLPostingService glPostingService;

    private BankChargeService service;
    private Currency usd;
    private UserAccount clerk;
    private Account bankChargesExpense;
    private Account bankGl;
    private BankAccount bank;

    @BeforeEach
    void setUp() {
        service = new BankChargeService(bankChargeRepository, bankAccountRepository, userAccountRepository, accountRepository, glPostingService);

        usd = Currency.builder().id(1L).code("USD").build();
        clerk = UserAccount.builder().id(1L).username("clerk1").password("x").email("clerk1@test.com").build();
        bankChargesExpense = Account.builder().id(1L).code("5500").name("Bank Charges")
                .accountType(AccountType.EXPENSE).normalBalance(DebitCredit.DEBIT).active(true).build();
        bankGl = Account.builder().id(2L).code("1030").name("Bank")
                .accountType(AccountType.ASSET).normalBalance(DebitCredit.DEBIT).active(true).build();
        bank = BankAccount.builder().id(11L).accountName("CBZ Main Account").accountType(CashBankAccountType.BANK)
                .glAccountCode("1030").currency(usd).currentBalance(new BigDecimal("200.00")).active(true).build();

        lenient().when(userAccountRepository.findById(1L)).thenReturn(Optional.of(clerk));
        lenient().when(accountRepository.findByCode("5500")).thenReturn(Optional.of(bankChargesExpense));
        lenient().when(accountRepository.findByCode("1030")).thenReturn(Optional.of(bankGl));
        lenient().when(bankAccountRepository.findById(11L)).thenReturn(Optional.of(bank));
        lenient().when(bankAccountRepository.save(any(BankAccount.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(bankChargeRepository.save(any(BankCharge.class))).thenAnswer(inv -> {
            BankCharge c = inv.getArgument(0);
            if (c.getId() == null) c.setId(50L);
            return c;
        });
    }

    private CreateBankChargeRequest request() {
        CreateBankChargeRequest request = new CreateBankChargeRequest();
        request.setReferenceNumber("CHG-1");
        request.setBankAccountId(11L);
        request.setAmount(new BigDecimal("15.00"));
        request.setChargeDate(LocalDate.of(2026, 8, 15));
        request.setCreatedByUserId(1L);
        return request;
    }

    @Test
    void createChargeRejectsADuplicateReference() {
        when(bankChargeRepository.existsByReferenceNumber("CHG-1")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> service.createCharge(request()));
    }

    @Test
    void createChargeRejectsInsufficientBalance() {
        when(bankChargeRepository.existsByReferenceNumber("CHG-1")).thenReturn(false);
        CreateBankChargeRequest request = request();
        request.setAmount(new BigDecimal("5000.00"));

        assertThrows(IllegalStateException.class, () -> service.createCharge(request));
        verifyNoInteractions(glPostingService);
    }

    @Test
    void createChargeDebitsExpenseAndCreditsTheBankAccount() {
        when(bankChargeRepository.existsByReferenceNumber("CHG-1")).thenReturn(false);
        JournalEntry entry = JournalEntry.builder().id(600L).entryNumber(60L).build();
        ArgumentCaptor<List<ManualLineSpec>> captor = ArgumentCaptor.forClass(List.class);
        when(glPostingService.postManual(eq("BANK-CHARGE-CHG-1"), eq(LocalDate.of(2026, 8, 15)), anyString(),
                any(), eq("BANK_CHARGE"), eq(50L), captor.capture(), eq("clerk1")))
                .thenReturn(entry);

        BankChargeResponse response = service.createCharge(request());

        assertEquals(60L, response.getJournalEntryNumber());
        assertEquals(0, new BigDecimal("185.00").compareTo(bank.getCurrentBalance()));

        List<ManualLineSpec> specs = captor.getValue();
        ManualLineSpec expenseLine = specs.stream().filter(s -> s.account() == bankChargesExpense).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("15.00").compareTo(expenseLine.debitAmount()));
        ManualLineSpec bankLine = specs.stream().filter(s -> s.account() == bankGl).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("15.00").compareTo(bankLine.creditAmount()));
    }
}
