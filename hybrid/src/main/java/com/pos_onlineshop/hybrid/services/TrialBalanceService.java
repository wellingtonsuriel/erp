package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.account.Account;
import com.pos_onlineshop.hybrid.account.AccountRepository;
import com.pos_onlineshop.hybrid.dtos.TrialBalanceReport;
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
 * Reads exclusively from JournalLine/Account - never from Order/OrderLine/ShopInventory - per
 * the rule that financial reports must reconcile back to the GL, not reconstruct accounting
 * from operational tables. This is the first report built on the new GL (see the
 * implementation summary for what's still missing: P&L, Balance Sheet, Cash Flow, VAT,
 * GL detail, aging, and everything else remain undone this pass).
 */
@Service
@RequiredArgsConstructor
public class TrialBalanceService {

    private final AccountRepository accountRepository;
    private final JournalLineRepository journalLineRepository;

    private record Totals(BigDecimal debit, BigDecimal credit) {
        static final Totals ZERO = new Totals(BigDecimal.ZERO, BigDecimal.ZERO);
    }

    @Transactional(readOnly = true)
    public TrialBalanceReport generate(LocalDate fromDate, LocalDate toDate, Long shopId) {
        List<Account> accounts = accountRepository.findByActiveTrue();

        Map<Long, Totals> opening = toTotalsMap(journalLineRepository.aggregateBeforeDate(fromDate, shopId));
        Map<Long, Totals> period = toTotalsMap(journalLineRepository.aggregateBetween(fromDate, toDate, shopId));

        List<TrialBalanceReport.AccountLine> lines = new ArrayList<>();
        BigDecimal totalOpeningDebit = BigDecimal.ZERO;
        BigDecimal totalOpeningCredit = BigDecimal.ZERO;
        BigDecimal totalPeriodDebit = BigDecimal.ZERO;
        BigDecimal totalPeriodCredit = BigDecimal.ZERO;

        for (Account account : accounts) {
            Totals o = opening.getOrDefault(account.getId(), Totals.ZERO);
            Totals p = period.getOrDefault(account.getId(), Totals.ZERO);

            BigDecimal closingDebit = o.debit().add(p.debit());
            BigDecimal closingCredit = o.credit().add(p.credit());

            // Skip accounts with no activity at all, to keep the report readable - this is a
            // display choice, not a data omission (totals below are computed from every
            // account, not just the ones shown).
            if (isZero(o.debit()) && isZero(o.credit()) && isZero(p.debit()) && isZero(p.credit())) {
                continue;
            }

            lines.add(TrialBalanceReport.AccountLine.builder()
                    .accountCode(account.getCode())
                    .accountName(account.getName())
                    .accountType(account.getAccountType().name())
                    .normalBalance(account.getNormalBalance().name())
                    .openingDebit(o.debit())
                    .openingCredit(o.credit())
                    .periodDebit(p.debit())
                    .periodCredit(p.credit())
                    .closingDebit(closingDebit)
                    .closingCredit(closingCredit)
                    .build());

            totalOpeningDebit = totalOpeningDebit.add(o.debit());
            totalOpeningCredit = totalOpeningCredit.add(o.credit());
            totalPeriodDebit = totalPeriodDebit.add(p.debit());
            totalPeriodCredit = totalPeriodCredit.add(p.credit());
        }

        BigDecimal totalClosingDebit = totalOpeningDebit.add(totalPeriodDebit);
        BigDecimal totalClosingCredit = totalOpeningCredit.add(totalPeriodCredit);

        return TrialBalanceReport.builder()
                .fromDate(fromDate)
                .toDate(toDate)
                .shopId(shopId)
                .accounts(lines)
                .totalOpeningDebit(totalOpeningDebit)
                .totalOpeningCredit(totalOpeningCredit)
                .totalPeriodDebit(totalPeriodDebit)
                .totalPeriodCredit(totalPeriodCredit)
                .totalClosingDebit(totalClosingDebit)
                .totalClosingCredit(totalClosingCredit)
                .balanced(totalClosingDebit.compareTo(totalClosingCredit) == 0)
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
