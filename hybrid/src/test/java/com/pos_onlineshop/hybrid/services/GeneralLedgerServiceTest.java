package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.account.Account;
import com.pos_onlineshop.hybrid.account.AccountRepository;
import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.dtos.AccountLedgerReport;
import com.pos_onlineshop.hybrid.enums.AccountType;
import com.pos_onlineshop.hybrid.enums.DebitCredit;
import com.pos_onlineshop.hybrid.enums.GLSourceModule;
import com.pos_onlineshop.hybrid.enums.JournalStatus;
import com.pos_onlineshop.hybrid.journalEntry.JournalEntry;
import com.pos_onlineshop.hybrid.journalLine.JournalLine;
import com.pos_onlineshop.hybrid.journalLine.JournalLineRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
class GeneralLedgerServiceTest {

    @Mock private AccountRepository accountRepository;
    @Mock private JournalLineRepository journalLineRepository;

    private GeneralLedgerService service;
    private Currency usd;
    private final LocalDate fromDate = LocalDate.of(2026, 8, 1);
    private final LocalDate toDate = LocalDate.of(2026, 8, 31);

    @BeforeEach
    void setUp() {
        service = new GeneralLedgerService(accountRepository, journalLineRepository);
        usd = Currency.builder().id(1L).code("USD").build();
    }

    private JournalLine line(Long id, Account account, LocalDate entryDate, BigDecimal debit, BigDecimal credit, Long entryId) {
        JournalEntry entry = JournalEntry.builder().id(entryId).entryNumber(entryId)
                .entryDate(entryDate).description("Entry " + entryId)
                .sourceModule(GLSourceModule.POS).sourceReferenceType("ORDER").sourceReferenceId(entryId)
                .status(JournalStatus.POSTED).build();
        return JournalLine.builder().id(id).journalEntry(entry).account(account)
                .debitAmount(debit).creditAmount(credit).currency(usd).baseAmount(debit.compareTo(BigDecimal.ZERO) > 0 ? debit : credit)
                .memo("line " + id).build();
    }

    @Test
    void debitNormalAccountRunningBalanceIncreasesOnDebitsAndDecreasesOnCredits() {
        Account cash = Account.builder().id(1L).code("1010").name("Cash on Hand")
                .accountType(AccountType.ASSET).normalBalance(DebitCredit.DEBIT).active(true).build();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(cash));
        when(journalLineRepository.sumDebitsForAccountBetween(eq(cash), any(), eq(fromDate.minusDays(1)))).thenReturn(new BigDecimal("1000.00"));
        when(journalLineRepository.sumCreditsForAccountBetween(eq(cash), any(), eq(fromDate.minusDays(1)))).thenReturn(new BigDecimal("200.00"));
        when(journalLineRepository.findLedgerLinesForAccountBetween(cash, fromDate, toDate, null)).thenReturn(List.of(
                line(1L, cash, LocalDate.of(2026, 8, 5), new BigDecimal("300.00"), BigDecimal.ZERO, 100L),
                line(2L, cash, LocalDate.of(2026, 8, 10), BigDecimal.ZERO, new BigDecimal("150.00"), 101L)));

        AccountLedgerReport report = service.generateAccountLedger(1L, fromDate, toDate, null);

        assertEquals(0, new BigDecimal("800.00").compareTo(report.getOpeningBalance())); // 1000 - 200
        assertEquals(2, report.getLines().size());
        assertEquals(0, new BigDecimal("1100.00").compareTo(report.getLines().get(0).getRunningBalance())); // 800 + 300
        assertEquals(0, new BigDecimal("950.00").compareTo(report.getLines().get(1).getRunningBalance())); // 1100 - 150
        assertEquals(0, new BigDecimal("950.00").compareTo(report.getClosingBalance()));
    }

    @Test
    void creditNormalAccountRunningBalanceIncreasesOnCreditsAndDecreasesOnDebits() {
        Account payable = Account.builder().id(2L).code("2100").name("Accounts Payable")
                .accountType(AccountType.LIABILITY).normalBalance(DebitCredit.CREDIT).active(true).build();
        when(accountRepository.findById(2L)).thenReturn(Optional.of(payable));
        when(journalLineRepository.sumDebitsForAccountBetween(eq(payable), any(), eq(fromDate.minusDays(1)))).thenReturn(BigDecimal.ZERO);
        when(journalLineRepository.sumCreditsForAccountBetween(eq(payable), any(), eq(fromDate.minusDays(1)))).thenReturn(new BigDecimal("500.00"));
        when(journalLineRepository.findLedgerLinesForAccountBetween(payable, fromDate, toDate, null)).thenReturn(List.of(
                line(3L, payable, LocalDate.of(2026, 8, 3), BigDecimal.ZERO, new BigDecimal("200.00"), 102L)));

        AccountLedgerReport report = service.generateAccountLedger(2L, fromDate, toDate, null);

        assertEquals(0, new BigDecimal("500.00").compareTo(report.getOpeningBalance()));
        assertEquals(0, new BigDecimal("700.00").compareTo(report.getLines().get(0).getRunningBalance()));
        assertEquals("CREDIT", report.getNormalBalance());
    }

    @Test
    void throwsWhenAccountNotFound() {
        when(accountRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.generateAccountLedger(99L, fromDate, toDate, null));
    }

    @Test
    void closingBalanceEqualsOpeningBalanceWhenThereIsNoActivityInRange() {
        Account cash = Account.builder().id(1L).code("1010").name("Cash on Hand")
                .accountType(AccountType.ASSET).normalBalance(DebitCredit.DEBIT).active(true).build();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(cash));
        when(journalLineRepository.sumDebitsForAccountBetween(eq(cash), any(), eq(fromDate.minusDays(1)))).thenReturn(new BigDecimal("500.00"));
        when(journalLineRepository.sumCreditsForAccountBetween(eq(cash), any(), eq(fromDate.minusDays(1)))).thenReturn(BigDecimal.ZERO);
        when(journalLineRepository.findLedgerLinesForAccountBetween(cash, fromDate, toDate, null)).thenReturn(List.of());

        AccountLedgerReport report = service.generateAccountLedger(1L, fromDate, toDate, null);

        assertTrue(report.getLines().isEmpty());
        assertEquals(0, report.getOpeningBalance().compareTo(report.getClosingBalance()));
    }
}
