package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.account.Account;
import com.pos_onlineshop.hybrid.account.AccountRepository;
import com.pos_onlineshop.hybrid.dtos.ProfitAndLossReport;
import com.pos_onlineshop.hybrid.enums.AccountType;
import com.pos_onlineshop.hybrid.enums.DebitCredit;
import com.pos_onlineshop.hybrid.journalLine.JournalLineRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfitAndLossServiceTest {

    @Mock private AccountRepository accountRepository;
    @Mock private JournalLineRepository journalLineRepository;

    private ProfitAndLossService service;

    private final LocalDate from = LocalDate.of(2026, 8, 1);
    private final LocalDate to = LocalDate.of(2026, 8, 31);

    private Account posRevenue;
    private Account salesReturns;
    private Account cogs;
    private Account operatingExpenses;

    @BeforeEach
    void setUp() {
        service = new ProfitAndLossService(accountRepository, journalLineRepository);

        posRevenue = Account.builder().id(1L).code("4000").name("Sales Revenue - POS")
                .accountType(AccountType.REVENUE).normalBalance(DebitCredit.CREDIT).active(true).build();
        salesReturns = Account.builder().id(2L).code("4900").name("Sales Returns & Allowances")
                .accountType(AccountType.REVENUE).normalBalance(DebitCredit.DEBIT).active(true).build();
        cogs = Account.builder().id(3L).code("5000").name("Cost of Goods Sold")
                .accountType(AccountType.EXPENSE).normalBalance(DebitCredit.DEBIT).costOfGoodsSold(true).active(true).build();
        operatingExpenses = Account.builder().id(4L).code("5300").name("Operating Expenses")
                .accountType(AccountType.EXPENSE).normalBalance(DebitCredit.DEBIT).active(true).build();
    }

    @Test
    void computesNetRevenueGrossProfitAndNetProfit() {
        when(accountRepository.findByActiveTrue()).thenReturn(List.of(posRevenue, salesReturns, cogs, operatingExpenses));
        when(journalLineRepository.aggregateBetween(from, to, null)).thenReturn(List.<Object[]>of(
                new Object[]{1L, BigDecimal.ZERO, new BigDecimal("1000.00")},        // revenue: credit 1000
                new Object[]{2L, new BigDecimal("100.00"), BigDecimal.ZERO},          // returns: debit 100 (contra)
                new Object[]{3L, new BigDecimal("400.00"), BigDecimal.ZERO},          // COGS: debit 400
                new Object[]{4L, new BigDecimal("150.00"), BigDecimal.ZERO}));        // opex: debit 150

        ProfitAndLossReport report = service.generate(from, to, null);

        // Net revenue = 1000 (POS) - 100 (returns) = 900
        assertEquals(0, new BigDecimal("900.00").compareTo(report.getNetRevenue()));
        assertEquals(0, new BigDecimal("400.00").compareTo(report.getTotalCostOfGoodsSold()));
        // Gross profit = 900 - 400 = 500
        assertEquals(0, new BigDecimal("500.00").compareTo(report.getGrossProfit()));
        assertEquals(0, new BigDecimal("150.00").compareTo(report.getTotalOperatingExpenses()));
        // Net profit = 500 - 150 = 350
        assertEquals(0, new BigDecimal("350.00").compareTo(report.getNetProfit()));
        assertEquals(2, report.getRevenueLines().size());
        assertEquals(1, report.getCostOfGoodsSoldLines().size());
        assertEquals(1, report.getOperatingExpenseLines().size());
    }

    @Test
    void grossMarginPercentIsComputedFromGrossProfitOverNetRevenue() {
        when(accountRepository.findByActiveTrue()).thenReturn(List.of(posRevenue, cogs));
        when(journalLineRepository.aggregateBetween(from, to, null)).thenReturn(List.<Object[]>of(
                new Object[]{1L, BigDecimal.ZERO, new BigDecimal("200.00")},
                new Object[]{3L, new BigDecimal("50.00"), BigDecimal.ZERO}));

        ProfitAndLossReport report = service.generate(from, to, null);

        // Gross profit 150 / revenue 200 * 100 = 75.00
        assertEquals(0, new BigDecimal("75.00").compareTo(report.getGrossMarginPercent()));
    }

    @Test
    void grossMarginPercentIsNullWhenThereIsNoRevenue() {
        when(accountRepository.findByActiveTrue()).thenReturn(List.of(cogs));
        when(journalLineRepository.aggregateBetween(from, to, null)).thenReturn(List.<Object[]>of(
                new Object[]{3L, new BigDecimal("50.00"), BigDecimal.ZERO}));

        ProfitAndLossReport report = service.generate(from, to, null);

        assertNull(report.getGrossMarginPercent());
    }

    @Test
    void accountsWithNoActivityInThePeriodAreOmitted() {
        when(accountRepository.findByActiveTrue()).thenReturn(List.of(posRevenue, salesReturns, cogs, operatingExpenses));
        when(journalLineRepository.aggregateBetween(from, to, null)).thenReturn(List.<Object[]>of(
                new Object[]{1L, BigDecimal.ZERO, new BigDecimal("1000.00")}));

        ProfitAndLossReport report = service.generate(from, to, null);

        assertEquals(1, report.getRevenueLines().size());
        assertTrue(report.getCostOfGoodsSoldLines().isEmpty());
        assertTrue(report.getOperatingExpenseLines().isEmpty());
    }
}
