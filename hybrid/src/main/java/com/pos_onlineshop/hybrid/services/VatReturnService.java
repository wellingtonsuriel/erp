package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.account.Account;
import com.pos_onlineshop.hybrid.account.AccountRepository;
import com.pos_onlineshop.hybrid.dtos.VatReturnReport;
import com.pos_onlineshop.hybrid.enums.AccountType;
import com.pos_onlineshop.hybrid.journalLine.JournalLineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** See VatReturnReport's class comment for what this does and does not capture. */
@Service
@RequiredArgsConstructor
public class VatReturnService {

    private static final String VAT_OUTPUT_ACCOUNT_CODE = "2200";
    private static final String VAT_INPUT_ACCOUNT_CODE = "1400";
    private static final String ACCOUNTS_PAYABLE_ACCOUNT_CODE = "2100";

    private final AccountRepository accountRepository;
    private final JournalLineRepository journalLineRepository;

    private record Totals(BigDecimal debit, BigDecimal credit) {
        static final Totals ZERO = new Totals(BigDecimal.ZERO, BigDecimal.ZERO);
    }

    @Transactional(readOnly = true)
    public VatReturnReport generate(LocalDate fromDate, LocalDate toDate, Long shopId) {
        Map<Long, Totals> period = toTotalsMap(journalLineRepository.aggregateBetween(fromDate, toDate, shopId));
        List<Account> accounts = accountRepository.findByActiveTrue();

        BigDecimal outputTax = netFor(period, accountByCode(accounts, VAT_OUTPUT_ACCOUNT_CODE), true);
        BigDecimal inputTax = netFor(period, accountByCode(accounts, VAT_INPUT_ACCOUNT_CODE), false);
        BigDecimal netTaxPayable = outputTax.subtract(inputTax);

        BigDecimal taxableSales = accounts.stream()
                .filter(a -> a.getAccountType() == AccountType.REVENUE)
                .map(a -> period.getOrDefault(a.getId(), Totals.ZERO))
                .map(t -> t.credit().subtract(t.debit()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal grossPurchases = accountByCode(accounts, ACCOUNTS_PAYABLE_ACCOUNT_CODE)
                .map(a -> period.getOrDefault(a.getId(), Totals.ZERO).credit())
                .orElse(BigDecimal.ZERO);
        BigDecimal taxablePurchases = grossPurchases.subtract(inputTax);

        return VatReturnReport.builder()
                .fromDate(fromDate)
                .toDate(toDate)
                .shopId(shopId)
                .outputTax(outputTax)
                .inputTax(inputTax)
                .netTaxPayable(netTaxPayable)
                .taxableSales(taxableSales)
                .taxablePurchases(taxablePurchases)
                .exemptSales(null)
                .zeroRatedSales(null)
                .build();
    }

    private Optional<Account> accountByCode(List<Account> accounts, String code) {
        return accounts.stream().filter(a -> code.equals(a.getCode())).findFirst();
    }

    /** liabilitySide true -> credit-normal (VAT Output); false -> debit-normal (VAT Input). */
    private BigDecimal netFor(Map<Long, Totals> period, Optional<Account> account, boolean liabilitySide) {
        Totals t = account.map(a -> period.getOrDefault(a.getId(), Totals.ZERO)).orElse(Totals.ZERO);
        return liabilitySide ? t.credit().subtract(t.debit()) : t.debit().subtract(t.credit());
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
