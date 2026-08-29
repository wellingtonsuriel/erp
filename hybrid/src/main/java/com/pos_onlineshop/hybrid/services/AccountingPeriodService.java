package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.account.Account;
import com.pos_onlineshop.hybrid.account.AccountRepository;
import com.pos_onlineshop.hybrid.accountingPeriod.AccountingPeriod;
import com.pos_onlineshop.hybrid.accountingPeriod.AccountingPeriodRepository;
import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.dtos.TrialBalanceReport;
import com.pos_onlineshop.hybrid.enums.AccountType;
import com.pos_onlineshop.hybrid.enums.GLSourceModule;
import com.pos_onlineshop.hybrid.enums.PeriodStatus;
import com.pos_onlineshop.hybrid.gl.ManualLineSpec;
import com.pos_onlineshop.hybrid.journalEntry.JournalEntry;
import com.pos_onlineshop.hybrid.journalLine.JournalLineRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Calendar-month accounting periods. getOrCreateMonthlyPeriod exists so the system always
 * has an OPEN period to post into without a manual setup step.
 *
 * closePeriod() runs every period-end automation that used to be reachable only through its
 * own explicit "/run" endpoint - accrual reversal, depreciation, FX revaluation, and (when
 * the business has ever recorded a price index reading) IAS 29 restatement - THEN validates
 * the period's trial balance, runs the revenue/expense-to-Retained-Earnings sweep (see
 * sweepRevenueAndExpenseToRetainedEarnings), then flips status to CLOSED. All of that must
 * happen in that order and while the period is still OPEN: depreciation and FX revaluation
 * post to expense accounts that the sweep needs to catch, and every step must be able to
 * post at all. None of this is optional or best-effort - if any step fails (e.g. an open
 * foreign-currency invoice has no exchange rate on record), the whole close aborts rather
 * than closing the period with an incomplete adjustment silently missing.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountingPeriodService {

    private static final DateTimeFormatter PERIOD_NAME = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final String RETAINED_EARNINGS_ACCOUNT_CODE = "3000";

    private final AccountingPeriodRepository accountingPeriodRepository;
    private final AccountRepository accountRepository;
    private final JournalLineRepository journalLineRepository;
    private final GLPostingService glPostingService;
    private final TrialBalanceService trialBalanceService;
    private final CurrencyService currencyService;
    private final AccrualService accrualService;
    private final AssetDepreciationService assetDepreciationService;
    private final FxRevaluationService fxRevaluationService;
    private final Ias29RestatementService ias29RestatementService;
    private final GeneralPriceIndexService generalPriceIndexService;

    private record Totals(BigDecimal debit, BigDecimal credit) {
        static final Totals ZERO = new Totals(BigDecimal.ZERO, BigDecimal.ZERO);
    }

    @Transactional(readOnly = true)
    public java.util.List<AccountingPeriod> findAll() {
        return accountingPeriodRepository.findAll();
    }

    @Transactional(readOnly = true)
    public AccountingPeriod findById(Long id) {
        return accountingPeriodRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Accounting period not found: " + id));
    }

    @Transactional
    public AccountingPeriod getOrCreateMonthlyPeriod(LocalDate date) {
        return accountingPeriodRepository.findContaining(date)
                .orElseGet(() -> createMonthlyPeriod(date));
    }

    private AccountingPeriod createMonthlyPeriod(LocalDate date) {
        YearMonth ym = YearMonth.from(date);
        String name = date.format(PERIOD_NAME);
        AccountingPeriod period = AccountingPeriod.builder()
                .name(name)
                .startDate(ym.atDay(1))
                .endDate(ym.atEndOfMonth())
                .status(PeriodStatus.OPEN)
                .build();
        AccountingPeriod saved = accountingPeriodRepository.save(period);
        log.info("GL: opened accounting period {}", name);
        return saved;
    }

    /**
     * 1. Reverses every accrual due for reversal on or before period end (AccrualService,
     *    idempotent - already-reversed accruals are simply not matched again).
     * 2. Runs straight-line depreciation for period end (AssetDepreciationService, idempotent
     *    per asset/period via a DB-unique constraint).
     * 3. Revalues every open foreign-currency AR/AP balance to period end's rate
     *    (FxRevaluationService, idempotent - a balance whose rate hasn't moved is skipped).
     * 4. Restates fixed assets to period end's price level (Ias29RestatementService,
     *    idempotent the same way) - but ONLY when the business has ever recorded a general
     *    price index reading; skipped entirely otherwise, since requiring one to close a
     *    period would break every business that has never opted into IAS 29.
     * 5. Validates the period's own trial balance is balanced - always expected true after
     *    the steps above (every individual posted entry already balances by construction,
     *    per JournalValidator), but asserted explicitly rather than assumed, per the "never
     *    silently force balance" rule used throughout the reporting services.
     * 6. Sweeps every REVENUE/EXPENSE account's net movement for the period into Retained
     *    Earnings via one idempotent GLPostingService.postManual() call (sourceModule SYSTEM).
     * 7. Marks the period CLOSED, rejecting any further posting into it from that point on.
     */
    @Transactional
    public AccountingPeriod closePeriod(Long periodId, String closedBy) {
        AccountingPeriod period = accountingPeriodRepository.findById(periodId)
                .orElseThrow(() -> new IllegalArgumentException("Accounting period not found: " + periodId));
        if (period.getStatus() != PeriodStatus.OPEN) {
            throw new IllegalStateException("Period " + period.getName() + " is already " + period.getStatus());
        }
        if (accountingPeriodRepository.existsByStartDateBeforeAndStatus(period.getStartDate(), PeriodStatus.OPEN)) {
            throw new IllegalStateException(
                    "Cannot close period " + period.getName() + ": an earlier period is still OPEN - "
                            + "periods must be closed in chronological order");
        }

        accrualService.reverseDueAccruals(period.getEndDate());
        assetDepreciationService.runMonthlyDepreciation(period.getEndDate(), closedBy);
        fxRevaluationService.revalueOpenBalances(period.getEndDate(), closedBy);
        if (generalPriceIndexService.hasAnyReadings()) {
            ias29RestatementService.restateFixedAssets(period.getEndDate(), closedBy);
        }

        TrialBalanceReport trialBalance = trialBalanceService.generate(period.getStartDate(), period.getEndDate(), null);
        if (!trialBalance.isBalanced()) {
            throw new IllegalStateException(
                    "Cannot close period " + period.getName() + ": trial balance is not balanced "
                            + "(debits=" + trialBalance.getTotalClosingDebit() + ", credits=" + trialBalance.getTotalClosingCredit() + ")");
        }

        JournalEntry closingEntry = sweepRevenueAndExpenseToRetainedEarnings(period, closedBy);

        period.setStatus(PeriodStatus.CLOSED);
        period.setClosedAt(LocalDateTime.now());
        period.setClosedBy(closedBy);
        period.setClosingJournalEntry(closingEntry);
        period.setCloseCount(period.getCloseCount() + 1);
        return accountingPeriodRepository.save(period);
    }

    /**
     * Closes every REVENUE account (crediting its net credit balance to zero, or debiting a
     * net debit balance to zero - a contra-revenue account like 4900 Sales Returns is
     * handled correctly either way) and every EXPENSE account the same way, with the
     * offsetting amount posted to Retained Earnings. If total revenue exactly equals total
     * expense for the period, the revenue- and expense-closing lines already balance each
     * other with no Retained Earnings line needed at all. Idempotency key is
     * "PERIOD-CLOSE-{periodId}-{closeCount+1}" - closeCount only advances once the whole close
     * transaction commits (see closePeriod), so a retried close after a transient failure
     * between the sweep and the status flip still computes the same key and replays
     * idempotently. It is deliberately NOT just "PERIOD-CLOSE-{periodId}", so that a close
     * following a reopen gets a fresh key rather than replaying the prior (now-reversed) sweep
     * entry untouched - see reopenPeriod for why the prior sweep must be reversed at all.
     */
    private JournalEntry sweepRevenueAndExpenseToRetainedEarnings(AccountingPeriod period, String closedBy) {
        Map<Long, Totals> periodActivity = toTotalsMap(
                journalLineRepository.aggregateBetween(period.getStartDate(), period.getEndDate(), null));
        List<Account> accounts = accountRepository.findByActiveTrue();
        Currency baseCurrency = currencyService.getBaseCurrency();

        List<ManualLineSpec> specs = new ArrayList<>();
        BigDecimal netIncome = BigDecimal.ZERO;

        for (Account account : accounts) {
            Totals t = periodActivity.getOrDefault(account.getId(), Totals.ZERO);
            if (isZero(t.debit()) && isZero(t.credit())) {
                continue;
            }

            if (account.getAccountType() == AccountType.REVENUE) {
                BigDecimal net = t.credit().subtract(t.debit());
                if (isZero(net)) {
                    continue;
                }
                specs.add(net.compareTo(BigDecimal.ZERO) > 0
                        ? debitSpec(account, net, baseCurrency, period.getName())
                        : creditSpec(account, net.negate(), baseCurrency, period.getName()));
                netIncome = netIncome.add(net);
            } else if (account.getAccountType() == AccountType.EXPENSE) {
                BigDecimal net = t.debit().subtract(t.credit());
                if (isZero(net)) {
                    continue;
                }
                specs.add(net.compareTo(BigDecimal.ZERO) > 0
                        ? creditSpec(account, net, baseCurrency, period.getName())
                        : debitSpec(account, net.negate(), baseCurrency, period.getName()));
                netIncome = netIncome.subtract(net);
            }
        }

        if (specs.isEmpty()) {
            log.info("GL: no revenue/expense activity to sweep for period {}", period.getName());
            return null;
        }

        Account retainedEarnings = accountRepository.findByCode(RETAINED_EARNINGS_ACCOUNT_CODE)
                .orElseThrow(() -> new IllegalStateException(
                        "Chart of accounts is missing " + RETAINED_EARNINGS_ACCOUNT_CODE + " Retained Earnings"));
        if (netIncome.compareTo(BigDecimal.ZERO) > 0) {
            specs.add(creditSpec(retainedEarnings, netIncome, baseCurrency, period.getName()));
        } else if (netIncome.compareTo(BigDecimal.ZERO) < 0) {
            specs.add(debitSpec(retainedEarnings, netIncome.negate(), baseCurrency, period.getName()));
        }

        JournalEntry closingEntry = glPostingService.postManual(
                "PERIOD-CLOSE-" + period.getId() + "-" + (period.getCloseCount() + 1),
                period.getEndDate(),
                "Period close: sweep revenue/expense to Retained Earnings for " + period.getName(),
                GLSourceModule.SYSTEM,
                "ACCOUNTING_PERIOD",
                period.getId(),
                specs,
                closedBy);
        log.info("GL: closed period {} with closing entry #{} (net income {})",
                period.getName(), closingEntry.getEntryNumber(), netIncome);
        return closingEntry;
    }

    private ManualLineSpec debitSpec(Account account, BigDecimal amount, Currency currency, String memo) {
        return new ManualLineSpec(account, amount, BigDecimal.ZERO, currency, BigDecimal.ONE, null, "Period close sweep: " + memo);
    }

    private ManualLineSpec creditSpec(Account account, BigDecimal amount, Currency currency, String memo) {
        return new ManualLineSpec(account, BigDecimal.ZERO, amount, currency, BigDecimal.ONE, null, "Period close sweep: " + memo);
    }

    private boolean isZero(BigDecimal value) {
        return value == null || value.compareTo(BigDecimal.ZERO) == 0;
    }

    private Map<Long, Totals> toTotalsMap(List<Object[]> rows) {
        Map<Long, Totals> map = new HashMap<>();
        for (Object[] row : rows) {
            Long accountId = (Long) row[0];
            BigDecimal debit = (BigDecimal) row[1];
            BigDecimal credit = (BigDecimal) row[2];
            map.put(accountId, new Totals(debit, credit));
        }
        return map;
    }

    /**
     * Reopens a CLOSED period so new entries can post into it again. If the period's last
     * close swept revenue/expense to Retained Earnings, that sweep entry is reversed first -
     * closePeriod's sweep recomputes from every JournalLine dated within the period, which
     * includes the sweep's own prior lines, so leaving it in place would double-count it the
     * next time this period closes. The next close computes a fresh idempotency key (see
     * sweepRevenueAndExpenseToRetainedEarnings), so it posts a new sweep rather than replaying
     * this reversed one.
     */
    @Transactional
    public AccountingPeriod reopenPeriod(Long periodId, String reopenedBy) {
        AccountingPeriod period = accountingPeriodRepository.findById(periodId)
                .orElseThrow(() -> new IllegalArgumentException("Accounting period not found: " + periodId));
        if (period.getStatus() == PeriodStatus.LOCKED) {
            throw new IllegalStateException("Period " + period.getName() + " is LOCKED and cannot be reopened");
        }
        if (period.getStatus() == PeriodStatus.OPEN) {
            throw new IllegalStateException("Period " + period.getName() + " is already OPEN");
        }
        if (accountingPeriodRepository.existsByStartDateAfterAndStatusIn(
                period.getStartDate(), List.of(PeriodStatus.CLOSED, PeriodStatus.LOCKED))) {
            throw new IllegalStateException(
                    "Cannot reopen period " + period.getName() + ": a later period has already closed - "
                            + "periods must be reopened in reverse chronological order");
        }

        if (period.getClosingJournalEntry() != null) {
            glPostingService.reverse(period.getClosingJournalEntry(), LocalDate.now(),
                    "Period " + period.getName() + " reopened", reopenedBy);
            period.setClosingJournalEntry(null);
        }

        period.setStatus(PeriodStatus.OPEN);
        period.setClosedAt(null);
        period.setClosedBy(null);
        return accountingPeriodRepository.save(period);
    }
}
