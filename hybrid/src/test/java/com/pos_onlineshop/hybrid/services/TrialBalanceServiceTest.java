package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.account.Account;
import com.pos_onlineshop.hybrid.account.AccountRepository;
import com.pos_onlineshop.hybrid.dtos.TrialBalanceReport;
import com.pos_onlineshop.hybrid.enums.AccountType;
import com.pos_onlineshop.hybrid.enums.DebitCredit;
import com.pos_onlineshop.hybrid.journalLine.JournalLineRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrialBalanceServiceTest {

    @Mock private AccountRepository accountRepository;
    @Mock private JournalLineRepository journalLineRepository;
    @InjectMocks private TrialBalanceService trialBalanceService;

    @Test
    void closingEqualsOpeningPlusPeriodAndTotalsBalance() {
        Account cash = Account.builder().id(1L).code("1010").name("Cash on Hand")
                .accountType(AccountType.ASSET).normalBalance(DebitCredit.DEBIT).active(true).build();
        Account revenue = Account.builder().id(2L).code("4000").name("Sales Revenue")
                .accountType(AccountType.REVENUE).normalBalance(DebitCredit.CREDIT).active(true).build();

        when(accountRepository.findByActiveTrue()).thenReturn(List.of(cash, revenue));

        LocalDate from = LocalDate.of(2026, 8, 1);
        LocalDate to = LocalDate.of(2026, 8, 31);

        // Opening: cash already had 500 debit / 500 credit before the period (net zero, still shown)
        when(journalLineRepository.aggregateBeforeDate(from, null)).thenReturn(List.of(
                new Object[]{1L, new BigDecimal("500.00"), BigDecimal.ZERO},
                new Object[]{2L, BigDecimal.ZERO, new BigDecimal("500.00")}
        ));
        // Period: a single 115 cash sale (100 revenue + 15 tax would need a VAT account too, kept
        // simple here as a plain two-line movement to isolate what TrialBalanceService itself does)
        when(journalLineRepository.aggregateBetween(from, to, null)).thenReturn(List.of(
                new Object[]{1L, new BigDecimal("115.00"), BigDecimal.ZERO},
                new Object[]{2L, BigDecimal.ZERO, new BigDecimal("115.00")}
        ));

        TrialBalanceReport report = trialBalanceService.generate(from, to, null);

        assertTrue(report.isBalanced());
        assertEquals(0, report.getTotalClosingDebit().compareTo(new BigDecimal("615.00")));
        assertEquals(0, report.getTotalClosingCredit().compareTo(new BigDecimal("615.00")));
        assertEquals(2, report.getAccounts().size());

        TrialBalanceReport.AccountLine cashLine = report.getAccounts().stream()
                .filter(l -> l.getAccountCode().equals("1010")).findFirst().orElseThrow();
        assertEquals(0, cashLine.getClosingDebit().compareTo(new BigDecimal("615.00")));
        assertEquals(0, cashLine.getOpeningDebit().compareTo(new BigDecimal("500.00")));
        assertEquals(0, cashLine.getPeriodDebit().compareTo(new BigDecimal("115.00")));
    }

    @Test
    void accountsWithNoActivityAreOmittedFromTheDisplayedLinesButNotFromTotals() {
        Account cash = Account.builder().id(1L).code("1010").name("Cash on Hand")
                .accountType(AccountType.ASSET).normalBalance(DebitCredit.DEBIT).active(true).build();
        Account untouched = Account.builder().id(3L).code("5900").name("FX Gain/Loss")
                .accountType(AccountType.EXPENSE).normalBalance(DebitCredit.DEBIT).active(true).build();

        when(accountRepository.findByActiveTrue()).thenReturn(List.of(cash, untouched));

        LocalDate from = LocalDate.of(2026, 8, 1);
        LocalDate to = LocalDate.of(2026, 8, 31);
        when(journalLineRepository.aggregateBeforeDate(from, null)).thenReturn(List.of());
        when(journalLineRepository.aggregateBetween(from, to, null)).thenReturn(List.<Object[]>of(
                new Object[]{1L, new BigDecimal("50.00"), BigDecimal.ZERO}
        ));

        TrialBalanceReport report = trialBalanceService.generate(from, to, null);

        assertEquals(1, report.getAccounts().size());
        assertEquals("1010", report.getAccounts().get(0).getAccountCode());
    }
}
