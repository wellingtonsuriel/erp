package com.pos_onlineshop.hybrid.dtos;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Every figure here is read from an existing authoritative report/service - never recomputed
 * independently - so the dashboard can never drift from Trial Balance / P&L / Balance Sheet /
 * VAT Return / AR & AP Aging / Control Account Reconciliation / Inventory Valuation.
 */
@Data
@Builder
public class AccountingDashboardResponse {

    private LocalDate asOfDate;

    private Long currentPeriodId;
    private String currentPeriodName;
    private String currentPeriodStatus;
    private LocalDate currentPeriodStart;
    private LocalDate currentPeriodEnd;

    /** 1010 Cash, cumulative balance as of asOfDate. */
    private BigDecimal cashBalance;
    /** 1030 Bank, cumulative balance as of asOfDate. */
    private BigDecimal bankBalance;

    private BigDecimal totalReceivables;
    private BigDecimal overdueReceivables;
    private BigDecimal totalPayables;
    private BigDecimal overduePayables;

    private BigDecimal inventoryValue;

    /** Period-to-date (currentPeriodStart..asOfDate), from the GL P&L. */
    private BigDecimal periodRevenue;
    private BigDecimal periodCostOfGoodsSold;
    private BigDecimal periodGrossProfit;
    private BigDecimal periodNetProfit;

    /** Period-to-date net VAT payable (outputTax - inputTax) from actual GL tax movements. */
    private BigDecimal vatPayable;

    private boolean trialBalanceBalanced;

    private int reconciliationLineCount;
    private int reconciliationVarianceCount;
    private boolean reconciliationClean;
}
