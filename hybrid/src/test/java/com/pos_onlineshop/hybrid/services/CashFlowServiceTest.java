package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.account.Account;
import com.pos_onlineshop.hybrid.account.AccountRepository;
import com.pos_onlineshop.hybrid.dtos.CashFlowReport;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CashFlowServiceTest {

    @Mock private AccountRepository accountRepository;
    @Mock private JournalLineRepository journalLineRepository;

    private CashFlowService service;

    private final LocalDate from = LocalDate.of(2026, 8, 1);
    private final LocalDate to = LocalDate.of(2026, 8, 31);

    private Account cash;
    private Account bank;
    private Account clearing;

    @BeforeEach
    void setUp() {
        service = new CashFlowService(accountRepository, journalLineRepository);

        cash = Account.builder().id(1L).code("1010").name("Cash on Hand")
                .accountType(AccountType.ASSET).normalBalance(DebitCredit.DEBIT).active(true).build();
        bank = Account.builder().id(2L).code("1030").name("Bank")
                .accountType(AccountType.ASSET).normalBalance(DebitCredit.DEBIT).active(true).build();
        clearing = Account.builder().id(3L).code("1020").name("Mobile Money / Card Clearing")
                .accountType(AccountType.ASSET).normalBalance(DebitCredit.DEBIT).active(true).build();

        when(accountRepository.findAll()).thenReturn(List.of(cash, bank, clearing));
    }

    @Test
    void computesOperatingCashFlowBrokenDownBySourceReferenceType() {
        when(journalLineRepository.aggregateBeforeDate(from, null)).thenReturn(List.<Object[]>of(
                new Object[]{1L, new BigDecimal("1000.00"), BigDecimal.ZERO}));   // opening cash 1000
        when(journalLineRepository.aggregateBeforeDate(to.plusDays(1), null)).thenReturn(List.<Object[]>of(
                new Object[]{1L, new BigDecimal("1300.00"), BigDecimal.ZERO}));   // closing cash 1300
        when(journalLineRepository.aggregateBySourceReferenceTypeForAccounts(
                eq(List.of(1L, 2L)), eq(from), eq(to), eq((Long) null))).thenReturn(List.<Object[]>of(
                new Object[]{"ORDER", new BigDecimal("400.00"), BigDecimal.ZERO},
                new Object[]{"CASHIER_SESSION", BigDecimal.ZERO, new BigDecimal("100.00")}));

        CashFlowReport report = service.generate(from, to, null);

        assertEquals(0, new BigDecimal("1000.00").compareTo(report.getOpeningCashBalance()));
        // 400 (ORDER) - 100 (CASHIER_SESSION) = 300
        assertEquals(0, new BigDecimal("300.00").compareTo(report.getNetOperatingCashFlow()));
        assertEquals(0, BigDecimal.ZERO.compareTo(report.getNetInvestingCashFlow()));
        assertEquals(0, BigDecimal.ZERO.compareTo(report.getNetFinancingCashFlow()));
        assertEquals(0, new BigDecimal("300.00").compareTo(report.getNetCashFlow()));
        assertEquals(0, new BigDecimal("1300.00").compareTo(report.getClosingCashBalance()));
        assertTrue(report.isReconciled());
        assertEquals(2, report.getOperatingActivities().size());
        assertTrue(report.getInvestingActivities().isEmpty());
        assertTrue(report.getFinancingActivities().isEmpty());
    }

    @Test
    void excludesMobileMoneyCardClearingFromCashBalances() {
        when(journalLineRepository.aggregateBeforeDate(from, null)).thenReturn(List.<Object[]>of(
                new Object[]{3L, new BigDecimal("5000.00"), BigDecimal.ZERO}));   // large clearing balance
        when(journalLineRepository.aggregateBeforeDate(to.plusDays(1), null)).thenReturn(List.<Object[]>of(
                new Object[]{3L, new BigDecimal("5000.00"), BigDecimal.ZERO}));
        when(journalLineRepository.aggregateBySourceReferenceTypeForAccounts(
                eq(List.of(1L, 2L)), eq(from), eq(to), eq((Long) null))).thenReturn(List.of());

        CashFlowReport report = service.generate(from, to, null);

        // The 5000 sitting in the clearing account must not appear as cash
        assertEquals(0, BigDecimal.ZERO.compareTo(report.getOpeningCashBalance()));
        assertEquals(0, BigDecimal.ZERO.compareTo(report.getClosingCashBalance()));
    }

    @Test
    void mapsUnknownSourceReferenceTypesToALabeledFallbackRatherThanDroppingThem() {
        when(journalLineRepository.aggregateBeforeDate(from, null)).thenReturn(List.of());
        when(journalLineRepository.aggregateBeforeDate(to.plusDays(1), null)).thenReturn(List.<Object[]>of(
                new Object[]{1L, new BigDecimal("75.00"), BigDecimal.ZERO}));
        when(journalLineRepository.aggregateBySourceReferenceTypeForAccounts(
                eq(List.of(1L, 2L)), eq(from), eq(to), eq((Long) null))).thenReturn(List.<Object[]>of(
                new Object[]{"SOME_NEW_MODULE", new BigDecimal("75.00"), BigDecimal.ZERO}));

        CashFlowReport report = service.generate(from, to, null);

        assertEquals(1, report.getOperatingActivities().size());
        assertTrue(report.getOperatingActivities().get(0).getLabel().contains("SOME_NEW_MODULE"));
    }

    @Test
    void zeroNetMovementLinesAreOmitted() {
        when(journalLineRepository.aggregateBeforeDate(from, null)).thenReturn(List.of());
        when(journalLineRepository.aggregateBeforeDate(to.plusDays(1), null)).thenReturn(List.of());
        when(journalLineRepository.aggregateBySourceReferenceTypeForAccounts(
                eq(List.of(1L, 2L)), eq(from), eq(to), eq((Long) null))).thenReturn(List.<Object[]>of(
                new Object[]{"ORDER", new BigDecimal("50.00"), new BigDecimal("50.00")}));

        CashFlowReport report = service.generate(from, to, null);

        assertTrue(report.getOperatingActivities().isEmpty());
    }
}
