package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.account.Account;
import com.pos_onlineshop.hybrid.account.AccountRepository;
import com.pos_onlineshop.hybrid.dtos.ProfitAndLossReport;
import com.pos_onlineshop.hybrid.enums.AccountType;
import com.pos_onlineshop.hybrid.journalLine.JournalLineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads exclusively from JournalLine/Account for the requested period - see
 * TrialBalanceService's class comment for why (the same rule applies here). Every EXPENSE
 * account whose Account.costOfGoodsSold flag is set contributes to Cost of Goods Sold;
 * every other EXPENSE account (Operating Expenses, Cash Over/Short, FX Gain/Loss,
 * Inventory Write-off in the starter chart) is bucketed together as "Operating Expenses" -
 * a simplification versus a fully categorized multi-step P&L, but it matches the two-bucket
 * structure this report is required to produce.
 */
@Service
@RequiredArgsConstructor
public class ProfitAndLossService {

    private final AccountRepository accountRepository;
    private final JournalLineRepository journalLineRepository;

    private record Totals(BigDecimal debit, BigDecimal credit) {
        static final Totals ZERO = new Totals(BigDecimal.ZERO, BigDecimal.ZERO);
    }

    @Transactional(readOnly = true)
    public ProfitAndLossReport generate(LocalDate fromDate, LocalDate toDate, Long shopId) {
        Map<Long, Totals> period = toTotalsMap(journalLineRepository.aggregateBetween(fromDate, toDate, shopId));

        List<Account> accounts = accountRepository.findByActiveTrue();

        List<ProfitAndLossReport.AccountLine> revenueLines = new ArrayList<>();
        List<ProfitAndLossReport.AccountLine> cogsLines = new ArrayList<>();
        List<ProfitAndLossReport.AccountLine> opexLines = new ArrayList<>();
        BigDecimal netRevenue = BigDecimal.ZERO;
        BigDecimal totalCogs = BigDecimal.ZERO;
        BigDecimal totalOpex = BigDecimal.ZERO;

        for (Account account : accounts) {
            Totals t = period.getOrDefault(account.getId(), Totals.ZERO);
            if (isZero(t.debit()) && isZero(t.credit())) {
                continue;
            }

            if (account.getAccountType() == AccountType.REVENUE) {
                // credit - debit works for both a normal (credit-normal) revenue account and a
                // contra-revenue account like Sales Returns (debit-normal): the contra account's
                // debit-heavy activity naturally nets out as a negative contribution.
                BigDecimal amount = t.credit().subtract(t.debit());
                revenueLines.add(line(account, amount));
                netRevenue = netRevenue.add(amount);
            } else if (account.getAccountType() == AccountType.EXPENSE) {
                BigDecimal amount = t.debit().subtract(t.credit());
                if (account.isCostOfGoodsSold()) {
                    cogsLines.add(line(account, amount));
                    totalCogs = totalCogs.add(amount);
                } else {
                    opexLines.add(line(account, amount));
                    totalOpex = totalOpex.add(amount);
                }
            }
        }

        BigDecimal grossProfit = netRevenue.subtract(totalCogs);
        BigDecimal grossMarginPercent = netRevenue.compareTo(BigDecimal.ZERO) == 0
                ? null
                : grossProfit.multiply(new BigDecimal("100")).divide(netRevenue, 2, RoundingMode.HALF_UP);
        BigDecimal netProfit = grossProfit.subtract(totalOpex);

        return ProfitAndLossReport.builder()
                .fromDate(fromDate)
                .toDate(toDate)
                .shopId(shopId)
                .revenueLines(revenueLines)
                .netRevenue(netRevenue)
                .costOfGoodsSoldLines(cogsLines)
                .totalCostOfGoodsSold(totalCogs)
                .grossProfit(grossProfit)
                .grossMarginPercent(grossMarginPercent)
                .operatingExpenseLines(opexLines)
                .totalOperatingExpenses(totalOpex)
                .netProfit(netProfit)
                .build();
    }

    private ProfitAndLossReport.AccountLine line(Account account, BigDecimal amount) {
        return ProfitAndLossReport.AccountLine.builder()
                .accountCode(account.getCode())
                .accountName(account.getName())
                .amount(amount)
                .build();
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
}
