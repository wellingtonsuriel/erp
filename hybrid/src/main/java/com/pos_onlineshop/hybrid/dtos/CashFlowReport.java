package com.pos_onlineshop.hybrid.dtos;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Direct-method cash flow, built from JournalLine activity on the true cash/bank accounts
 * (1010 Cash on Hand, 1030 Bank) only - see CashFlowService's class comment for why 1020
 * Mobile Money/Card Clearing is deliberately excluded (it is a clearing/suspense position,
 * not settled cash, and this system has no settlement event that moves it to 1030 yet).
 *
 * investingActivities and financingActivities are currently always empty: this chart of
 * accounts has no Fixed Assets, loan, or equity-contribution accounts yet (see the master
 * roadmap's Fixed Assets/Banking phases), so no GL activity of either nature exists to
 * report. They are real empty lists, not omitted fields, so a consumer can tell "nothing
 * happened" from "not implemented" only by this note - once those modules post real
 * investing/financing cash movements, this report picks them up without further change to
 * the operating-activities logic.
 */
@Data
@Builder
public class CashFlowReport {

    private LocalDate fromDate;
    private LocalDate toDate;
    private Long shopId;

    private BigDecimal openingCashBalance;

    private List<Line> operatingActivities;
    private BigDecimal netOperatingCashFlow;

    private List<Line> investingActivities;
    private BigDecimal netInvestingCashFlow;

    private List<Line> financingActivities;
    private BigDecimal netFinancingCashFlow;

    private BigDecimal netCashFlow;
    private BigDecimal closingCashBalance;

    /** True only if openingCashBalance + netCashFlow equals the actual cumulative cash
     * balance independently computed through toDate - reported explicitly rather than
     * assumed, per the "never silently force balance" rule used throughout these reports. */
    private boolean reconciled;

    @Data
    @Builder
    public static class Line {
        private String label;
        private BigDecimal amount;
    }
}
