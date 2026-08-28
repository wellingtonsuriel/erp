package com.pos_onlineshop.hybrid.gl;

import com.pos_onlineshop.hybrid.account.Account;
import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.shop.Shop;

import java.math.BigDecimal;

/** One fully-specified line for GLPostingService.postManual() - unlike FinancialEvent,
 * the account and debit/credit amount are given directly rather than resolved through a
 * PostingRule, because a manual journal's accounts are chosen by the preparer, not fixed. */
public record ManualLineSpec(
        Account account,
        BigDecimal debitAmount,
        BigDecimal creditAmount,
        Currency currency,
        BigDecimal exchangeRate,
        Shop costCenterShop,
        String memo
) {
}
