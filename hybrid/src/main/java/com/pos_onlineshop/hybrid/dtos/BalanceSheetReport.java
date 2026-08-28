package com.pos_onlineshop.hybrid.dtos;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class BalanceSheetReport {

    private LocalDate asOfDate;
    private Long shopId;

    private List<AccountLine> assetLines;
    private BigDecimal totalAssets;

    private List<AccountLine> liabilityLines;
    private BigDecimal totalLiabilities;

    /** The real EQUITY-typed accounts (Opening Balance Equity, Retained Earnings) plus one
     * synthetic "Current Period Earnings (Unswept)" line - see the class comment on
     * BalanceSheetService for why that line exists rather than being folded into Retained
     * Earnings. */
    private List<AccountLine> equityLines;
    private BigDecimal totalEquity;

    /** True only if totalAssets == totalLiabilities + totalEquity. Reported explicitly
     * rather than assumed, per the "never silently force balance" rule. */
    private boolean balanced;

    @Data
    @Builder
    public static class AccountLine {
        /** Null for the synthetic unswept-earnings line. */
        private String accountCode;
        private String accountName;
        private BigDecimal balance;
    }
}
