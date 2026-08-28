package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.account.Account;
import com.pos_onlineshop.hybrid.account.AccountRepository;
import com.pos_onlineshop.hybrid.dtos.BalanceSheetReport;
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
class BalanceSheetServiceTest {

    @Mock private AccountRepository accountRepository;
    @Mock private JournalLineRepository journalLineRepository;

    private BalanceSheetService service;

    private final LocalDate asOfDate = LocalDate.of(2026, 8, 31);

    private Account cash;
    private Account inventory;
    private Account accountsPayable;
    private Account openingEquity;
    private Account posRevenue;
    private Account cogs;

    @BeforeEach
    void setUp() {
        service = new BalanceSheetService(accountRepository, journalLineRepository);

        cash = Account.builder().id(1L).code("1010").name("Cash on Hand")
                .accountType(AccountType.ASSET).normalBalance(DebitCredit.DEBIT).active(true).build();
        inventory = Account.builder().id(2L).code("1200").name("Inventory Asset")
                .accountType(AccountType.ASSET).normalBalance(DebitCredit.DEBIT).controlAccount(true).active(true).build();
        accountsPayable = Account.builder().id(3L).code("2100").name("Accounts Payable")
                .accountType(AccountType.LIABILITY).normalBalance(DebitCredit.CREDIT).controlAccount(true).active(true).build();
        openingEquity = Account.builder().id(4L).code("3900").name("Opening Balance Equity")
                .accountType(AccountType.EQUITY).normalBalance(DebitCredit.CREDIT).active(true).build();
        posRevenue = Account.builder().id(5L).code("4000").name("Sales Revenue - POS")
                .accountType(AccountType.REVENUE).normalBalance(DebitCredit.CREDIT).active(true).build();
        cogs = Account.builder().id(6L).code("5000").name("Cost of Goods Sold")
                .accountType(AccountType.EXPENSE).normalBalance(DebitCredit.DEBIT).costOfGoodsSold(true).active(true).build();
    }

    @Test
    void assetsEqualLiabilitiesPlusEquityIncludingUnsweptEarnings() {
        when(accountRepository.findByActiveTrue()).thenReturn(
                List.of(cash, inventory, accountsPayable, openingEquity, posRevenue, cogs));
        when(journalLineRepository.aggregateBeforeDate(asOfDate.plusDays(1), null)).thenReturn(List.<Object[]>of(
                new Object[]{1L, new BigDecimal("500.00"), BigDecimal.ZERO},          // cash: debit 500
                new Object[]{2L, new BigDecimal("300.00"), BigDecimal.ZERO},          // inventory: debit 300
                new Object[]{3L, BigDecimal.ZERO, new BigDecimal("200.00")},          // AP: credit 200
                new Object[]{4L, BigDecimal.ZERO, new BigDecimal("400.00")},          // opening equity: credit 400
                new Object[]{5L, BigDecimal.ZERO, new BigDecimal("300.00")},          // revenue: credit 300
                new Object[]{6L, new BigDecimal("100.00"), BigDecimal.ZERO}));        // COGS: debit 100

        BalanceSheetReport report = service.generate(asOfDate, null);

        // Assets = 500 + 300 = 800
        assertEquals(0, new BigDecimal("800.00").compareTo(report.getTotalAssets()));
        // Liabilities = 200
        assertEquals(0, new BigDecimal("200.00").compareTo(report.getTotalLiabilities()));
        // Equity = opening 400 + unswept net income (300 revenue - 100 COGS = 200) = 600
        assertEquals(0, new BigDecimal("600.00").compareTo(report.getTotalEquity()));
        // 800 == 200 + 600
        assertTrue(report.isBalanced());
        assertEquals(2, report.getAssetLines().size());
        assertEquals(1, report.getLiabilityLines().size());
        // Opening equity account + synthetic unswept-earnings line
        assertEquals(2, report.getEquityLines().size());
    }

    @Test
    void addsASyntheticUnsweptEarningsLineLabeledExplicitly() {
        when(accountRepository.findByActiveTrue()).thenReturn(List.of(posRevenue, cogs));
        when(journalLineRepository.aggregateBeforeDate(asOfDate.plusDays(1), null)).thenReturn(List.<Object[]>of(
                new Object[]{5L, BigDecimal.ZERO, new BigDecimal("300.00")},
                new Object[]{6L, new BigDecimal("100.00"), BigDecimal.ZERO}));

        BalanceSheetReport report = service.generate(asOfDate, null);

        assertEquals(1, report.getEquityLines().size());
        BalanceSheetReport.AccountLine unswept = report.getEquityLines().get(0);
        assertNull(unswept.getAccountCode());
        assertEquals("Current Period Earnings (Unswept)", unswept.getAccountName());
        assertEquals(0, new BigDecimal("200.00").compareTo(unswept.getBalance()));
    }

    @Test
    void omitsTheSyntheticLineWhenThereIsNoUnsweptNetIncome() {
        when(accountRepository.findByActiveTrue()).thenReturn(List.of(cash, openingEquity));
        when(journalLineRepository.aggregateBeforeDate(asOfDate.plusDays(1), null)).thenReturn(List.<Object[]>of(
                new Object[]{1L, new BigDecimal("400.00"), BigDecimal.ZERO},
                new Object[]{4L, BigDecimal.ZERO, new BigDecimal("400.00")}));

        BalanceSheetReport report = service.generate(asOfDate, null);

        assertEquals(1, report.getEquityLines().size());
        assertTrue(report.isBalanced());
    }

    @Test
    void accountsWithNoCumulativeActivityAreOmitted() {
        when(accountRepository.findByActiveTrue()).thenReturn(List.of(cash, inventory, openingEquity));
        when(journalLineRepository.aggregateBeforeDate(asOfDate.plusDays(1), null)).thenReturn(List.<Object[]>of(
                new Object[]{1L, new BigDecimal("100.00"), BigDecimal.ZERO},
                new Object[]{4L, BigDecimal.ZERO, new BigDecimal("100.00")}));

        BalanceSheetReport report = service.generate(asOfDate, null);

        assertEquals(1, report.getAssetLines().size());
    }
}
