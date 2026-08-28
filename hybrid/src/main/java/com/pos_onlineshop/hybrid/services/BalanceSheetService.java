package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.account.Account;
import com.pos_onlineshop.hybrid.account.AccountRepository;
import com.pos_onlineshop.hybrid.dtos.BalanceSheetReport;
import com.pos_onlineshop.hybrid.enums.AccountType;
import com.pos_onlineshop.hybrid.journalLine.JournalLineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads exclusively from JournalLine/Account, cumulative from inception through asOfDate -
 * see TrialBalanceService's class comment for why. Every ASSET/LIABILITY/EQUITY account
 * balance is its lifetime debit/credit activity net of its normal balance side.
 *
 * AccountingPeriodService.closePeriod() does not (yet - see its own class comment) sweep
 * REVENUE/EXPENSE accounts into Retained Earnings, so their balances keep accumulating
 * indefinitely rather than resetting each period. Until that sweep exists, this report
 * folds that accumulated, not-yet-closed net income into equity as an explicit synthetic
 * "Current Period Earnings (Unswept)" line rather than silently omitting it (which would
 * make the balance sheet fail to balance against real asset/liability activity) or silently
 * pretending it's Retained Earnings (which would misstate a balance nobody actually posted).
 */
@Service
@RequiredArgsConstructor
public class BalanceSheetService {

    private final AccountRepository accountRepository;
    private final JournalLineRepository journalLineRepository;

    private record Totals(BigDecimal debit, BigDecimal credit) {
        static final Totals ZERO = new Totals(BigDecimal.ZERO, BigDecimal.ZERO);
    }

    @Transactional(readOnly = true)
    public BalanceSheetReport generate(LocalDate asOfDate, Long shopId) {
        Map<Long, Totals> cumulative = toTotalsMap(
                journalLineRepository.aggregateBeforeDate(asOfDate.plusDays(1), shopId));
        List<Account> accounts = accountRepository.findByActiveTrue();

        List<BalanceSheetReport.AccountLine> assetLines = new ArrayList<>();
        List<BalanceSheetReport.AccountLine> liabilityLines = new ArrayList<>();
        List<BalanceSheetReport.AccountLine> equityLines = new ArrayList<>();
        BigDecimal totalAssets = BigDecimal.ZERO;
        BigDecimal totalLiabilities = BigDecimal.ZERO;
        BigDecimal totalEquity = BigDecimal.ZERO;
        BigDecimal unsweptNetIncome = BigDecimal.ZERO;

        for (Account account : accounts) {
            Totals t = cumulative.getOrDefault(account.getId(), Totals.ZERO);
            if (isZero(t.debit()) && isZero(t.credit())) {
                continue;
            }

            switch (account.getAccountType()) {
                case ASSET -> {
                    BigDecimal balance = t.debit().subtract(t.credit());
                    assetLines.add(line(account, balance));
                    totalAssets = totalAssets.add(balance);
                }
                case LIABILITY -> {
                    BigDecimal balance = t.credit().subtract(t.debit());
                    liabilityLines.add(line(account, balance));
                    totalLiabilities = totalLiabilities.add(balance);
                }
                case EQUITY -> {
                    BigDecimal balance = t.credit().subtract(t.debit());
                    equityLines.add(line(account, balance));
                    totalEquity = totalEquity.add(balance);
                }
                case REVENUE -> unsweptNetIncome = unsweptNetIncome.add(t.credit().subtract(t.debit()));
                case EXPENSE -> unsweptNetIncome = unsweptNetIncome.subtract(t.debit().subtract(t.credit()));
            }
        }

        if (!isZero(unsweptNetIncome)) {
            equityLines.add(BalanceSheetReport.AccountLine.builder()
                    .accountCode(null)
                    .accountName("Current Period Earnings (Unswept)")
                    .balance(unsweptNetIncome)
                    .build());
            totalEquity = totalEquity.add(unsweptNetIncome);
        }

        return BalanceSheetReport.builder()
                .asOfDate(asOfDate)
                .shopId(shopId)
                .assetLines(assetLines)
                .totalAssets(totalAssets)
                .liabilityLines(liabilityLines)
                .totalLiabilities(totalLiabilities)
                .equityLines(equityLines)
                .totalEquity(totalEquity)
                .balanced(totalAssets.compareTo(totalLiabilities.add(totalEquity)) == 0)
                .build();
    }

    private BalanceSheetReport.AccountLine line(Account account, BigDecimal balance) {
        return BalanceSheetReport.AccountLine.builder()
                .accountCode(account.getCode())
                .accountName(account.getName())
                .balance(balance)
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
