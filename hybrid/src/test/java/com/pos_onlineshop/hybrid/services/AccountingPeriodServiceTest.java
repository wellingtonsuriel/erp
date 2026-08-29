package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.account.Account;
import com.pos_onlineshop.hybrid.account.AccountRepository;
import com.pos_onlineshop.hybrid.accountingPeriod.AccountingPeriod;
import com.pos_onlineshop.hybrid.accountingPeriod.AccountingPeriodRepository;
import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.dtos.TrialBalanceReport;
import com.pos_onlineshop.hybrid.enums.AccountType;
import com.pos_onlineshop.hybrid.enums.DebitCredit;
import com.pos_onlineshop.hybrid.enums.GLSourceModule;
import com.pos_onlineshop.hybrid.enums.PeriodStatus;
import com.pos_onlineshop.hybrid.gl.ManualLineSpec;
import com.pos_onlineshop.hybrid.journalEntry.JournalEntry;
import com.pos_onlineshop.hybrid.journalLine.JournalLineRepository;
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
class AccountingPeriodServiceTest {

    @Mock private AccountingPeriodRepository accountingPeriodRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private JournalLineRepository journalLineRepository;
    @Mock private GLPostingService glPostingService;
    @Mock private TrialBalanceService trialBalanceService;
    @Mock private CurrencyService currencyService;
    @Mock private AccrualService accrualService;
    @Mock private AssetDepreciationService assetDepreciationService;
    @Mock private FxRevaluationService fxRevaluationService;
    @Mock private Ias29RestatementService ias29RestatementService;
    @Mock private GeneralPriceIndexService generalPriceIndexService;

    private AccountingPeriodService service;

    private final LocalDate start = LocalDate.of(2026, 8, 1);
    private final LocalDate end = LocalDate.of(2026, 8, 31);
    private Currency currency;
    private Account posRevenue;
    private Account salesReturns;
    private Account cogs;
    private Account retainedEarnings;

    @BeforeEach
    void setUp() {
        service = new AccountingPeriodService(accountingPeriodRepository, accountRepository,
                journalLineRepository, glPostingService, trialBalanceService, currencyService,
                accrualService, assetDepreciationService, fxRevaluationService, ias29RestatementService, generalPriceIndexService);

        currency = Currency.builder().id(1L).code("USD").build();
        posRevenue = Account.builder().id(1L).code("4000").name("Sales Revenue - POS")
                .accountType(AccountType.REVENUE).normalBalance(DebitCredit.CREDIT).active(true).build();
        salesReturns = Account.builder().id(2L).code("4900").name("Sales Returns & Allowances")
                .accountType(AccountType.REVENUE).normalBalance(DebitCredit.DEBIT).active(true).build();
        cogs = Account.builder().id(3L).code("5000").name("Cost of Goods Sold")
                .accountType(AccountType.EXPENSE).normalBalance(DebitCredit.DEBIT).costOfGoodsSold(true).active(true).build();
        retainedEarnings = Account.builder().id(4L).code("3000").name("Retained Earnings")
                .accountType(AccountType.EQUITY).normalBalance(DebitCredit.CREDIT).active(true).build();
    }

    private AccountingPeriod openPeriod() {
        return AccountingPeriod.builder().id(1L).name("2026-08").startDate(start).endDate(end)
                .status(PeriodStatus.OPEN).build();
    }

    private TrialBalanceReport balancedTrialBalance() {
        return TrialBalanceReport.builder().balanced(true)
                .totalClosingDebit(BigDecimal.ZERO).totalClosingCredit(BigDecimal.ZERO).build();
    }

    @Test
    void closePeriodRejectsAnUnbalancedTrialBalance() {
        AccountingPeriod period = openPeriod();
        when(accountingPeriodRepository.findById(1L)).thenReturn(Optional.of(period));
        when(trialBalanceService.generate(start, end, null)).thenReturn(
                TrialBalanceReport.builder().balanced(false)
                        .totalClosingDebit(new BigDecimal("100")).totalClosingCredit(new BigDecimal("90")).build());

        assertThrows(IllegalStateException.class, () -> service.closePeriod(1L, "admin1"));
        verifyNoInteractions(glPostingService);
    }

    @Test
    void closePeriodCannotCloseAnAlreadyClosedPeriod() {
        AccountingPeriod period = openPeriod();
        period.setStatus(PeriodStatus.CLOSED);
        when(accountingPeriodRepository.findById(1L)).thenReturn(Optional.of(period));

        assertThrows(IllegalStateException.class, () -> service.closePeriod(1L, "admin1"));
    }

    @Test
    void closePeriodSweepsNetProfitToRetainedEarningsAsACredit() {
        AccountingPeriod period = openPeriod();
        when(accountingPeriodRepository.findById(1L)).thenReturn(Optional.of(period));
        when(trialBalanceService.generate(start, end, null)).thenReturn(balancedTrialBalance());
        when(accountRepository.findByActiveTrue()).thenReturn(List.of(posRevenue, cogs));
        when(accountRepository.findByCode("3000")).thenReturn(Optional.of(retainedEarnings));
        when(currencyService.getBaseCurrency()).thenReturn(currency);
        when(journalLineRepository.aggregateBetween(start, end, null)).thenReturn(List.<Object[]>of(
                new Object[]{1L, BigDecimal.ZERO, new BigDecimal("1000.00")},   // revenue: credit 1000
                new Object[]{3L, new BigDecimal("400.00"), BigDecimal.ZERO}));  // COGS: debit 400
        JournalEntry closingEntry = JournalEntry.builder().id(99L).entryNumber(50L).build();
        when(glPostingService.postManual(eq("PERIOD-CLOSE-1-1"), eq(end), anyString(), eq(GLSourceModule.SYSTEM),
                eq("ACCOUNTING_PERIOD"), eq(1L), anyList(), eq("admin1"))).thenReturn(closingEntry);
        when(accountingPeriodRepository.save(any(AccountingPeriod.class))).thenAnswer(inv -> inv.getArgument(0));

        AccountingPeriod result = service.closePeriod(1L, "admin1");

        assertEquals(PeriodStatus.CLOSED, result.getStatus());
        assertEquals("admin1", result.getClosedBy());
        assertNotNull(result.getClosedAt());

        ArgumentCaptor<List<ManualLineSpec>> captor = ArgumentCaptor.forClass(List.class);
        verify(glPostingService).postManual(eq("PERIOD-CLOSE-1-1"), eq(end), anyString(), eq(GLSourceModule.SYSTEM),
                eq("ACCOUNTING_PERIOD"), eq(1L), captor.capture(), eq("admin1"));
        List<ManualLineSpec> specs = captor.getValue();
        // revenue debit 1000 (closing it to zero) + COGS credit 400 (closing it to zero)
        // + Retained Earnings credit 600 (net profit) = 3 lines
        assertEquals(3, specs.size());

        BigDecimal totalDebits = specs.stream().map(ManualLineSpec::debitAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCredits = specs.stream().map(ManualLineSpec::creditAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, totalDebits.compareTo(totalCredits));

        ManualLineSpec reLine = specs.stream().filter(s -> s.account() == retainedEarnings).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("600.00").compareTo(reLine.creditAmount()));
    }

    @Test
    void closePeriodSweepsNetLossToRetainedEarningsAsADebit() {
        AccountingPeriod period = openPeriod();
        when(accountingPeriodRepository.findById(1L)).thenReturn(Optional.of(period));
        when(trialBalanceService.generate(start, end, null)).thenReturn(balancedTrialBalance());
        when(accountRepository.findByActiveTrue()).thenReturn(List.of(posRevenue, cogs));
        when(accountRepository.findByCode("3000")).thenReturn(Optional.of(retainedEarnings));
        when(currencyService.getBaseCurrency()).thenReturn(currency);
        when(journalLineRepository.aggregateBetween(start, end, null)).thenReturn(List.<Object[]>of(
                new Object[]{1L, BigDecimal.ZERO, new BigDecimal("100.00")},    // revenue: credit 100
                new Object[]{3L, new BigDecimal("400.00"), BigDecimal.ZERO}));  // COGS: debit 400 -> net loss 300
        JournalEntry closingEntry = JournalEntry.builder().id(99L).entryNumber(51L).build();
        when(glPostingService.postManual(anyString(), any(), anyString(), eq(GLSourceModule.SYSTEM),
                anyString(), anyLong(), anyList(), anyString())).thenReturn(closingEntry);
        when(accountingPeriodRepository.save(any(AccountingPeriod.class))).thenAnswer(inv -> inv.getArgument(0));

        service.closePeriod(1L, "admin1");

        ArgumentCaptor<List<ManualLineSpec>> captor = ArgumentCaptor.forClass(List.class);
        verify(glPostingService).postManual(anyString(), any(), anyString(), eq(GLSourceModule.SYSTEM),
                anyString(), anyLong(), captor.capture(), anyString());
        ManualLineSpec reLine = captor.getValue().stream().filter(s -> s.account() == retainedEarnings).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("300.00").compareTo(reLine.debitAmount()));
    }

    @Test
    void closePeriodHandlesAContraRevenueAccountCorrectly() {
        AccountingPeriod period = openPeriod();
        when(accountingPeriodRepository.findById(1L)).thenReturn(Optional.of(period));
        when(trialBalanceService.generate(start, end, null)).thenReturn(balancedTrialBalance());
        when(accountRepository.findByActiveTrue()).thenReturn(List.of(posRevenue, salesReturns));
        when(accountRepository.findByCode("3000")).thenReturn(Optional.of(retainedEarnings));
        when(currencyService.getBaseCurrency()).thenReturn(currency);
        when(journalLineRepository.aggregateBetween(start, end, null)).thenReturn(List.<Object[]>of(
                new Object[]{1L, BigDecimal.ZERO, new BigDecimal("1000.00")},   // revenue: credit 1000
                new Object[]{2L, new BigDecimal("100.00"), BigDecimal.ZERO}));  // returns (contra): debit 100
        JournalEntry closingEntry = JournalEntry.builder().id(99L).entryNumber(52L).build();
        when(glPostingService.postManual(anyString(), any(), anyString(), eq(GLSourceModule.SYSTEM),
                anyString(), anyLong(), anyList(), anyString())).thenReturn(closingEntry);
        when(accountingPeriodRepository.save(any(AccountingPeriod.class))).thenAnswer(inv -> inv.getArgument(0));

        service.closePeriod(1L, "admin1");

        ArgumentCaptor<List<ManualLineSpec>> captor = ArgumentCaptor.forClass(List.class);
        verify(glPostingService).postManual(anyString(), any(), anyString(), eq(GLSourceModule.SYSTEM),
                anyString(), anyLong(), captor.capture(), anyString());
        List<ManualLineSpec> specs = captor.getValue();

        // Sales Returns is a contra-revenue account sitting in a net DEBIT balance of 100 - to
        // close it to zero it must be CREDITED, not debited.
        ManualLineSpec returnsLine = specs.stream().filter(s -> s.account() == salesReturns).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("100.00").compareTo(returnsLine.creditAmount()));

        // Net revenue = 1000 - 100 = 900, credited to Retained Earnings
        ManualLineSpec reLine = specs.stream().filter(s -> s.account() == retainedEarnings).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("900.00").compareTo(reLine.creditAmount()));
    }

    @Test
    void closePeriodSkipsTheSweepEntirelyWhenThereIsNoRevenueOrExpenseActivity() {
        AccountingPeriod period = openPeriod();
        when(accountingPeriodRepository.findById(1L)).thenReturn(Optional.of(period));
        when(trialBalanceService.generate(start, end, null)).thenReturn(balancedTrialBalance());
        when(accountRepository.findByActiveTrue()).thenReturn(List.of(posRevenue, cogs));
        when(journalLineRepository.aggregateBetween(start, end, null)).thenReturn(List.of());
        when(accountingPeriodRepository.save(any(AccountingPeriod.class))).thenAnswer(inv -> inv.getArgument(0));

        AccountingPeriod result = service.closePeriod(1L, "admin1");

        assertEquals(PeriodStatus.CLOSED, result.getStatus());
        verifyNoInteractions(glPostingService);
    }

    @Test
    void closePeriodRunsAccrualReversalDepreciationAndFxRevaluationBeforeTheSweep() {
        AccountingPeriod period = openPeriod();
        when(accountingPeriodRepository.findById(1L)).thenReturn(Optional.of(period));
        when(trialBalanceService.generate(start, end, null)).thenReturn(balancedTrialBalance());
        when(accountRepository.findByActiveTrue()).thenReturn(List.of());
        when(journalLineRepository.aggregateBetween(start, end, null)).thenReturn(List.of());
        when(accountingPeriodRepository.save(any(AccountingPeriod.class))).thenAnswer(inv -> inv.getArgument(0));

        service.closePeriod(1L, "admin1");

        verify(accrualService).reverseDueAccruals(end);
        verify(assetDepreciationService).runMonthlyDepreciation(end, "admin1");
        verify(fxRevaluationService).revalueOpenBalances(end, "admin1");
    }

    @Test
    void closePeriodSkipsIas29RestatementWhenNoPriceIndexHasEverBeenRecorded() {
        AccountingPeriod period = openPeriod();
        when(accountingPeriodRepository.findById(1L)).thenReturn(Optional.of(period));
        when(trialBalanceService.generate(start, end, null)).thenReturn(balancedTrialBalance());
        when(accountRepository.findByActiveTrue()).thenReturn(List.of());
        when(journalLineRepository.aggregateBetween(start, end, null)).thenReturn(List.of());
        when(accountingPeriodRepository.save(any(AccountingPeriod.class))).thenAnswer(inv -> inv.getArgument(0));
        when(generalPriceIndexService.hasAnyReadings()).thenReturn(false);

        service.closePeriod(1L, "admin1");

        verifyNoInteractions(ias29RestatementService);
    }

    @Test
    void closePeriodRunsIas29RestatementWhenAPriceIndexHasBeenRecorded() {
        AccountingPeriod period = openPeriod();
        when(accountingPeriodRepository.findById(1L)).thenReturn(Optional.of(period));
        when(trialBalanceService.generate(start, end, null)).thenReturn(balancedTrialBalance());
        when(accountRepository.findByActiveTrue()).thenReturn(List.of());
        when(journalLineRepository.aggregateBetween(start, end, null)).thenReturn(List.of());
        when(accountingPeriodRepository.save(any(AccountingPeriod.class))).thenAnswer(inv -> inv.getArgument(0));
        when(generalPriceIndexService.hasAnyReadings()).thenReturn(true);

        service.closePeriod(1L, "admin1");

        verify(ias29RestatementService).restateFixedAssets(end, "admin1");
    }

    @Test
    void reopenPeriodRejectsALockedPeriod() {
        AccountingPeriod period = openPeriod();
        period.setStatus(PeriodStatus.LOCKED);
        when(accountingPeriodRepository.findById(1L)).thenReturn(Optional.of(period));

        assertThrows(IllegalStateException.class, () -> service.reopenPeriod(1L, "admin1"));
    }

    @Test
    void reopenPeriodRejectsAnAlreadyOpenPeriod() {
        AccountingPeriod period = openPeriod();
        when(accountingPeriodRepository.findById(1L)).thenReturn(Optional.of(period));

        assertThrows(IllegalStateException.class, () -> service.reopenPeriod(1L, "admin1"));
        verifyNoInteractions(glPostingService);
    }

    @Test
    void closePeriodRejectsClosingOutOfChronologicalOrder() {
        AccountingPeriod period = openPeriod();
        when(accountingPeriodRepository.findById(1L)).thenReturn(Optional.of(period));
        when(accountingPeriodRepository.existsByStartDateBeforeAndStatus(start, PeriodStatus.OPEN)).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> service.closePeriod(1L, "admin1"));
        verifyNoInteractions(trialBalanceService, glPostingService);
    }

    @Test
    void reopenPeriodRejectsWhenALaterPeriodHasAlreadyClosed() {
        AccountingPeriod period = openPeriod();
        period.setStatus(PeriodStatus.CLOSED);
        when(accountingPeriodRepository.findById(1L)).thenReturn(Optional.of(period));
        when(accountingPeriodRepository.existsByStartDateAfterAndStatusIn(
                start, List.of(PeriodStatus.CLOSED, PeriodStatus.LOCKED))).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> service.reopenPeriod(1L, "admin1"));
        verifyNoInteractions(glPostingService);
    }

    @Test
    void reopenPeriodReversesThePriorClosingEntryAndClearsIt() {
        AccountingPeriod period = openPeriod();
        period.setStatus(PeriodStatus.CLOSED);
        JournalEntry priorClosingEntry = JournalEntry.builder().id(99L).entryNumber(50L).build();
        period.setClosingJournalEntry(priorClosingEntry);
        period.setCloseCount(1);
        when(accountingPeriodRepository.findById(1L)).thenReturn(Optional.of(period));
        when(accountingPeriodRepository.save(any(AccountingPeriod.class))).thenAnswer(inv -> inv.getArgument(0));

        AccountingPeriod result = service.reopenPeriod(1L, "admin1");

        verify(glPostingService).reverse(eq(priorClosingEntry), any(LocalDate.class),
                eq("Period 2026-08 reopened"), eq("admin1"));
        assertNull(result.getClosingJournalEntry());
        assertEquals(PeriodStatus.OPEN, result.getStatus());
    }

    @Test
    void reclosingAReopenedPeriodUsesAFreshIdempotencyKeyReflectingCloseCount() {
        AccountingPeriod period = openPeriod();
        period.setCloseCount(1); // already closed once before
        when(accountingPeriodRepository.findById(1L)).thenReturn(Optional.of(period));
        when(trialBalanceService.generate(start, end, null)).thenReturn(balancedTrialBalance());
        when(accountRepository.findByActiveTrue()).thenReturn(List.of(posRevenue, cogs));
        when(accountRepository.findByCode("3000")).thenReturn(Optional.of(retainedEarnings));
        when(currencyService.getBaseCurrency()).thenReturn(currency);
        when(journalLineRepository.aggregateBetween(start, end, null)).thenReturn(List.<Object[]>of(
                new Object[]{1L, BigDecimal.ZERO, new BigDecimal("500.00")},
                new Object[]{3L, new BigDecimal("200.00"), BigDecimal.ZERO}));
        JournalEntry secondClosingEntry = JournalEntry.builder().id(150L).entryNumber(80L).build();
        when(glPostingService.postManual(eq("PERIOD-CLOSE-1-2"), eq(end), anyString(), eq(GLSourceModule.SYSTEM),
                eq("ACCOUNTING_PERIOD"), eq(1L), anyList(), eq("admin1"))).thenReturn(secondClosingEntry);
        when(accountingPeriodRepository.save(any(AccountingPeriod.class))).thenAnswer(inv -> inv.getArgument(0));

        AccountingPeriod result = service.closePeriod(1L, "admin1");

        assertEquals(2, result.getCloseCount());
        assertEquals(secondClosingEntry, result.getClosingJournalEntry());
        verify(glPostingService).postManual(eq("PERIOD-CLOSE-1-2"), eq(end), anyString(), eq(GLSourceModule.SYSTEM),
                eq("ACCOUNTING_PERIOD"), eq(1L), anyList(), eq("admin1"));
    }
}
