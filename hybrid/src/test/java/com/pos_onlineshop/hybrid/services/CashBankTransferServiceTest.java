package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.account.Account;
import com.pos_onlineshop.hybrid.account.AccountRepository;
import com.pos_onlineshop.hybrid.bankAccount.BankAccount;
import com.pos_onlineshop.hybrid.bankAccount.BankAccountRepository;
import com.pos_onlineshop.hybrid.cashBankTransfer.CashBankTransfer;
import com.pos_onlineshop.hybrid.cashBankTransfer.CashBankTransferRepository;
import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.dtos.CashBankTransferResponse;
import com.pos_onlineshop.hybrid.dtos.CreateCashBankTransferRequest;
import com.pos_onlineshop.hybrid.enums.AccountType;
import com.pos_onlineshop.hybrid.enums.CashBankAccountType;
import com.pos_onlineshop.hybrid.enums.CashBankTransferType;
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
class CashBankTransferServiceTest {

    @Mock private CashBankTransferRepository cashBankTransferRepository;
    @Mock private BankAccountRepository bankAccountRepository;
    @Mock private UserAccountRepository userAccountRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private GLPostingService glPostingService;

    private CashBankTransferService service;
    private Currency usd;
    private UserAccount clerk;
    private Account cashGl;
    private Account bankGl;
    private BankAccount till;
    private BankAccount bank;

    @BeforeEach
    void setUp() {
        service = new CashBankTransferService(cashBankTransferRepository, bankAccountRepository,
                userAccountRepository, accountRepository, glPostingService);

        usd = Currency.builder().id(1L).code("USD").build();
        clerk = UserAccount.builder().id(1L).username("clerk1").password("x").email("clerk1@test.com").build();
        cashGl = Account.builder().id(1L).code("1010").name("Cash on Hand")
                .accountType(AccountType.ASSET).normalBalance(DebitCredit.DEBIT).active(true).build();
        bankGl = Account.builder().id(2L).code("1030").name("Bank")
                .accountType(AccountType.ASSET).normalBalance(DebitCredit.DEBIT).active(true).build();
        till = BankAccount.builder().id(10L).accountName("Shop Till").accountType(CashBankAccountType.CASH)
                .glAccountCode("1010").currency(usd).currentBalance(new BigDecimal("1000.00")).active(true).build();
        bank = BankAccount.builder().id(11L).accountName("CBZ Main Account").accountType(CashBankAccountType.BANK)
                .glAccountCode("1030").currency(usd).currentBalance(new BigDecimal("200.00")).active(true).build();

        lenient().when(userAccountRepository.findById(1L)).thenReturn(Optional.of(clerk));
        lenient().when(accountRepository.findByCode("1010")).thenReturn(Optional.of(cashGl));
        lenient().when(accountRepository.findByCode("1030")).thenReturn(Optional.of(bankGl));
        lenient().when(bankAccountRepository.findById(10L)).thenReturn(Optional.of(till));
        lenient().when(bankAccountRepository.findById(11L)).thenReturn(Optional.of(bank));
        lenient().when(bankAccountRepository.save(any(BankAccount.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(cashBankTransferRepository.save(any(CashBankTransfer.class))).thenAnswer(inv -> {
            CashBankTransfer t = inv.getArgument(0);
            if (t.getId() == null) t.setId(30L);
            return t;
        });
    }

    private CreateCashBankTransferRequest request() {
        CreateCashBankTransferRequest request = new CreateCashBankTransferRequest();
        request.setReferenceNumber("XFER-1");
        request.setTransferType(CashBankTransferType.DEPOSIT);
        request.setFromAccountId(10L);
        request.setToAccountId(11L);
        request.setAmount(new BigDecimal("300.00"));
        request.setTransferDate(LocalDate.of(2026, 8, 15));
        request.setCreatedByUserId(1L);
        return request;
    }

    @Test
    void createTransferRejectsADuplicateReference() {
        when(cashBankTransferRepository.existsByReferenceNumber("XFER-1")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> service.createTransfer(request()));
    }

    @Test
    void createTransferRejectsTheSameSourceAndDestinationAccount() {
        when(cashBankTransferRepository.existsByReferenceNumber("XFER-1")).thenReturn(false);
        CreateCashBankTransferRequest request = request();
        request.setToAccountId(10L);

        assertThrows(IllegalArgumentException.class, () -> service.createTransfer(request));
    }

    @Test
    void createTransferRejectsInsufficientBalance() {
        when(cashBankTransferRepository.existsByReferenceNumber("XFER-1")).thenReturn(false);
        CreateCashBankTransferRequest request = request();
        request.setAmount(new BigDecimal("5000.00"));

        assertThrows(IllegalStateException.class, () -> service.createTransfer(request));
        verifyNoInteractions(glPostingService);
    }

    @Test
    void createTransferMovesBalanceAndPostsABalancedEntry() {
        when(cashBankTransferRepository.existsByReferenceNumber("XFER-1")).thenReturn(false);
        JournalEntry entry = JournalEntry.builder().id(400L).entryNumber(40L).build();
        ArgumentCaptor<List<ManualLineSpec>> captor = ArgumentCaptor.forClass(List.class);
        when(glPostingService.postManual(eq("CASHBANK-TRANSFER-XFER-1"), eq(LocalDate.of(2026, 8, 15)), anyString(),
                any(), eq("CASH_BANK_TRANSFER"), eq(30L), captor.capture(), eq("clerk1")))
                .thenReturn(entry);

        CashBankTransferResponse response = service.createTransfer(request());

        assertEquals("DEPOSIT", response.getTransferType());
        assertEquals(0, new BigDecimal("700.00").compareTo(till.getCurrentBalance()));
        assertEquals(0, new BigDecimal("500.00").compareTo(bank.getCurrentBalance()));

        List<ManualLineSpec> specs = captor.getValue();
        BigDecimal totalDebits = specs.stream().map(ManualLineSpec::debitAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCredits = specs.stream().map(ManualLineSpec::creditAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, totalDebits.compareTo(totalCredits));
        ManualLineSpec debitLine = specs.stream().filter(s -> s.account() == bankGl).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("300.00").compareTo(debitLine.debitAmount()));
        ManualLineSpec creditLine = specs.stream().filter(s -> s.account() == cashGl).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("300.00").compareTo(creditLine.creditAmount()));
    }
}
