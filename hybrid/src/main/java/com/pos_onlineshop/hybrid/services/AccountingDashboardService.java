package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.accountingPeriod.AccountingPeriod;
import com.pos_onlineshop.hybrid.dtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Composes the accounting dashboard entirely from existing authoritative reports/services -
 * Balance Sheet (cash/bank/AR/AP control balances), AR/AP Aging, Profit & Loss, VAT Return,
 * Inventory Valuation, and Control Account Reconciliation. This service performs no financial
 * calculation of its own; it only reads and re-shapes figures those services already compute,
 * per the rule that the frontend (and any other consumer) must never reconstruct accounting
 * figures independently.
 */
@Service
@RequiredArgsConstructor
public class AccountingDashboardService {

    private static final String CASH_ACCOUNT_CODE = "1010";
    private static final String BANK_ACCOUNT_CODE = "1030";
    private static final String CURRENT_BUCKET = "Current";

    private final AccountingPeriodService accountingPeriodService;
    private final BalanceSheetService balanceSheetService;
    private final ArAgingService arAgingService;
    private final ApAgingService apAgingService;
    private final ProfitAndLossService profitAndLossService;
    private final VatReturnService vatReturnService;
    private final InventoryValuationService inventoryValuationService;
    private final ControlAccountReconciliationService controlAccountReconciliationService;
    private final TrialBalanceService trialBalanceService;

    @Transactional(readOnly = true)
    public AccountingDashboardResponse getSummary(LocalDate asOfDate) {
        LocalDate effectiveDate = asOfDate != null ? asOfDate : LocalDate.now();

        AccountingPeriod currentPeriod = accountingPeriodService.getOrCreateMonthlyPeriod(effectiveDate);

        BalanceSheetReport balanceSheet = balanceSheetService.generate(effectiveDate, null);
        BigDecimal cashBalance = findAccountBalance(balanceSheet, CASH_ACCOUNT_CODE);
        BigDecimal bankBalance = findAccountBalance(balanceSheet, BANK_ACCOUNT_CODE);

        ArAgingReport arAging = arAgingService.generate(effectiveDate);
        BigDecimal totalReceivables = nz(arAging.getTotalOutstanding());
        BigDecimal currentReceivables = nz(arAging.getBucketTotals() != null
                ? arAging.getBucketTotals().get(CURRENT_BUCKET) : null);
        BigDecimal overdueReceivables = totalReceivables.subtract(currentReceivables);

        ApAgingReport apAging = apAgingService.generate(effectiveDate);
        BigDecimal totalPayables = nz(apAging.getTotalOutstanding());
        BigDecimal currentPayables = nz(apAging.getBucketTotals() != null
                ? apAging.getBucketTotals().get(CURRENT_BUCKET) : null);
        BigDecimal overduePayables = totalPayables.subtract(currentPayables);

        BigDecimal inventoryValue = inventoryValuationService.getTotalInventoryValue();

        ProfitAndLossReport pnl = profitAndLossService.generate(currentPeriod.getStartDate(), effectiveDate, null);
        VatReturnReport vat = vatReturnService.generate(currentPeriod.getStartDate(), effectiveDate, null);

        TrialBalanceReport trialBalance = trialBalanceService.generate(currentPeriod.getStartDate(), effectiveDate, null);

        ControlAccountReconciliationReport reconciliation = controlAccountReconciliationService.generate(effectiveDate);
        int lineCount = reconciliation.getLines() != null ? reconciliation.getLines().size() : 0;
        long varianceCount = reconciliation.getLines() == null ? 0 :
                reconciliation.getLines().stream().filter(l -> !l.isMatched()).count();

        return AccountingDashboardResponse.builder()
                .asOfDate(effectiveDate)
                .currentPeriodId(currentPeriod.getId())
                .currentPeriodName(currentPeriod.getName())
                .currentPeriodStatus(currentPeriod.getStatus().name())
                .currentPeriodStart(currentPeriod.getStartDate())
                .currentPeriodEnd(currentPeriod.getEndDate())
                .cashBalance(cashBalance)
                .bankBalance(bankBalance)
                .totalReceivables(totalReceivables)
                .overdueReceivables(overdueReceivables)
                .totalPayables(totalPayables)
                .overduePayables(overduePayables)
                .inventoryValue(inventoryValue)
                .periodRevenue(pnl.getNetRevenue())
                .periodCostOfGoodsSold(pnl.getTotalCostOfGoodsSold())
                .periodGrossProfit(pnl.getGrossProfit())
                .periodNetProfit(pnl.getNetProfit())
                .vatPayable(vat.getNetTaxPayable())
                .trialBalanceBalanced(trialBalance.isBalanced())
                .reconciliationLineCount(lineCount)
                .reconciliationVarianceCount((int) varianceCount)
                .reconciliationClean(varianceCount == 0)
                .build();
    }

    private BigDecimal findAccountBalance(BalanceSheetReport balanceSheet, String accountCode) {
        if (balanceSheet.getAssetLines() == null) {
            return BigDecimal.ZERO;
        }
        return balanceSheet.getAssetLines().stream()
                .filter(l -> accountCode.equals(l.getAccountCode()))
                .map(BalanceSheetReport.AccountLine::getBalance)
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }

    private BigDecimal nz(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
