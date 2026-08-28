package com.pos_onlineshop.hybrid.dtos;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Built entirely from JournalLine/Account for the given period - never from
 * Order/OrderLine, per the rule that P&L must read from the GL, not reconstruct
 * accounting from operational tables. */
@Data
@Builder
public class ProfitAndLossReport {

    private LocalDate fromDate;
    private LocalDate toDate;
    private Long shopId;

    private List<AccountLine> revenueLines;
    private BigDecimal netRevenue;

    private List<AccountLine> costOfGoodsSoldLines;
    private BigDecimal totalCostOfGoodsSold;

    private BigDecimal grossProfit;
    /** grossProfit / netRevenue * 100, or null when netRevenue is zero (undefined, not 0%). */
    private BigDecimal grossMarginPercent;

    private List<AccountLine> operatingExpenseLines;
    private BigDecimal totalOperatingExpenses;

    private BigDecimal netProfit;

    @Data
    @Builder
    public static class AccountLine {
        private String accountCode;
        private String accountName;
        /** Signed contribution to this section's total - a contra account (e.g. Sales
         * Returns, which is REVENUE-typed but debit-normal) naturally comes out negative. */
        private BigDecimal amount;
    }
}
