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
 * closePeriod() validates the period's trial balance, runs the revenue/expense-to-Retained-
 * Earnings sweep (see sweepRevenueAndExpenseToRetainedEarnings), then flips status to CLOSED
 * - in that order, since the sweep must post while the period is still OPEN.
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
     * 1. Validates the period's own trial balance is balanced - always expected true (every
     *    individual posted entry already balances by construction, per JournalValidator), but
     *    asserted explicitly rather than assumed, per the "never silently force balance" rule
     *    used throughout the reporting services.
     * 2. Sweeps every REVENUE/EXPENSE account's net movement for the period into Retained
     *    Earnings via one idempotent GLPostingService.postManual() call (sourceModule SYSTEM).
     * 3. Marks the period CLOSED, rejecting any further posting into it from that point on.
     */
    @Transactional
    public AccountingPeriod closePeriod(Long periodId, String closedBy) {
        AccountingPeriod period = accountingPeriodRepository.findById(periodId)
                .orElseThrow(() -> new IllegalArgumentException("Accounting period not found: " + periodId));
        if (period.getStatus() != PeriodStatus.OPEN) {
            throw new IllegalStateException("Period " + period.getName() + " is already " + period.getStatus());
        }

        TrialBalanceReport trialBalance = trialBalanceService.generate(period.getStartDate(), period.getEndDate(), null);
        if (!trialBalance.isBalanced()) {
            throw new IllegalStateException(
                    "Cannot close period " + period.getName() + ": trial balance is not balanced "
                            + "(debits=" + trialBalance.getTotalClosingDebit() + ", credits=" + trialBalance.getTotalClosingCredit() + ")");
        }

        sweepRevenueAndExpenseToRetainedEarnings(period, closedBy);

        period.setStatus(PeriodStatus.CLOSED);
        period.setClosedAt(LocalDateTime.now());
        period.setClosedBy(closedBy);
        return accountingPeriodRepository.save(period);
    }

    /**
     * Closes every REVENUE account (crediting its net credit balance to zero, or debiting a
     * net debit balance to zero - a contra-revenue account like 4900 Sales Returns is
     * handled correctly either way) and every EXPENSE account the same way, with the
     * offsetting amount posted to Retained Earnings. If total revenue exactly equals total
     * expense for the period, the revenue- and expense-closing lines already balance each
     * other with no Retained Earnings line needed at all. Idempotency key is
     * "PERIOD-CLOSE-{periodId}", so a retried close (e.g. after a transient failure between
     * the sweep and the status flip) never double-posts.
     */
    private void sweepRevenueAndExpenseToRetainedEarnings(AccountingPeriod period, String closedBy) {
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
            return;
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
                "PERIOD-CLOSE-" + period.getId(),
                period.getEndDate(),
                "Period close: sweep revenue/expense to Retained Earnings for " + period.getName(),
                GLSourceModule.SYSTEM,
                "ACCOUNTING_PERIOD",
                period.getId(),
                specs,
                closedBy);
        log.info("GL: closed period {} with closing entry #{} (net income {})",
                period.getName(), closingEntry.getEntryNumber(), netIncome);
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

    @Transactional
    public AccountingPeriod reopenPeriod(Long periodId) {
        AccountingPeriod period = accountingPeriodRepository.findById(periodId)
                .orElseThrow(() -> new IllegalArgumentException("Accounting period not found: " + periodId));
        if (period.getStatus() == PeriodStatus.LOCKED) {
            throw new IllegalStateException("Period " + period.getName() + " is LOCKED and cannot be reopened");
        }
        period.setStatus(PeriodStatus.OPEN);
        period.setClosedAt(null);
        period.setClosedBy(null);
        return accountingPeriodRepository.save(period);
    }
}
