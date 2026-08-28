package com.pos_onlineshop.hybrid.dtos;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class TrialBalanceReport {

    private LocalDate fromDate;
    private LocalDate toDate;
    private Long shopId;

    private List<AccountLine> accounts;

    private BigDecimal totalOpeningDebit;
    private BigDecimal totalOpeningCredit;
    private BigDecimal totalPeriodDebit;
    private BigDecimal totalPeriodCredit;
    private BigDecimal totalClosingDebit;
    private BigDecimal totalClosingCredit;

    /** True only if totalClosingDebit == totalClosingCredit. A real GL should never be false;
     * this is reported explicitly rather than assumed, per the "never silently force balance"
     * rule - if this is ever false it means a defect slipped past JournalValidator. */
    private boolean balanced;

    @Data
    @Builder
    public static class AccountLine {
        private String accountCode;
        private String accountName;
        private String accountType;
        private String normalBalance;
        private BigDecimal openingDebit;
        private BigDecimal openingCredit;
        private BigDecimal periodDebit;
        private BigDecimal periodCredit;
        private BigDecimal closingDebit;
        private BigDecimal closingCredit;
    }
}
