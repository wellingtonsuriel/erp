package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.account.Account;
import com.pos_onlineshop.hybrid.account.AccountRepository;
import com.pos_onlineshop.hybrid.dtos.CashFlowReport;
import com.pos_onlineshop.hybrid.journalLine.JournalLineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Direct-method cash flow read from JournalLine activity on the true cash/bank accounts
 * only (1010 Cash on Hand, 1030 Bank). 1020 Mobile Money/Card Clearing is deliberately
 * excluded - it is a clearing/suspense position representing money not yet settled to the
 * bank, and this system has no settlement FinancialEventType that ever moves a balance from
 * 1020 into 1030, so counting 1020 here would misrepresent unsettled clearing balances as
 * cash on hand (the exact thing the master rule "do not count non-cash clearing movements
 * as actual bank/cash" warns against).
 *
 * Operating activities are broken down by JournalEntry.sourceReferenceType (ORDER,
 * CUSTOMER_RECEIPT, SUPPLIER_PAYMENT, CASHIER_SESSION, ...) since JournalEntry does not
 * store which FinancialEventType produced it - sourceReferenceType is the closest existing
 * classification and is set consistently by every posting service (see the services package).
 */
@Service
@RequiredArgsConstructor
public class CashFlowService {

    private static final List<String> CASH_ACCOUNT_CODES = List.of("1010", "1030");

    private static final Map<String, String> OPERATING_LABELS = Map.of(
            "ORDER", "Cash Sales & Paid Online Orders",
            "CUSTOMER_RECEIPT", "Receipts from Customers",
            "SUPPLIER_PAYMENT", "Payments to Suppliers",
            "CASHIER_SESSION", "Cash Session Adjustments (Over/Short)",
            "MANUAL_JOURNAL", "Manual Journal Cash Movements",
            "OTHER", "Other Operating Activity");

    private final AccountRepository accountRepository;
    private final JournalLineRepository journalLineRepository;

    @Transactional(readOnly = true)
    public CashFlowReport generate(LocalDate fromDate, LocalDate toDate, Long shopId) {
        List<Long> cashAccountIds = accountRepository.findAll().stream()
                .filter(a -> CASH_ACCOUNT_CODES.contains(a.getCode()))
                .map(Account::getId)
                .collect(Collectors.toList());

        BigDecimal openingCashBalance = netCashBalance(
                journalLineRepository.aggregateBeforeDate(fromDate, shopId), cashAccountIds);
        BigDecimal actualClosingCashBalance = netCashBalance(
                journalLineRepository.aggregateBeforeDate(toDate.plusDays(1), shopId), cashAccountIds);

        List<CashFlowReport.Line> operatingActivities = new ArrayList<>();
        BigDecimal netOperatingCashFlow = BigDecimal.ZERO;
        for (Object[] row : journalLineRepository.aggregateBySourceReferenceTypeForAccounts(
                cashAccountIds, fromDate, toDate, shopId)) {
            String sourceReferenceType = (String) row[0];
            BigDecimal debit = (BigDecimal) row[1];
            BigDecimal credit = (BigDecimal) row[2];
            BigDecimal net = debit.subtract(credit);
            if (net.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            String label = OPERATING_LABELS.getOrDefault(sourceReferenceType,
                    "Other Operating Activity (" + sourceReferenceType + ")");
            operatingActivities.add(CashFlowReport.Line.builder().label(label).amount(net).build());
            netOperatingCashFlow = netOperatingCashFlow.add(net);
        }

        // No Fixed Assets, loan, or equity-contribution accounts exist in this chart yet - see
        // the class-level Javadoc on CashFlowReport for why these stay real empty lists.
        List<CashFlowReport.Line> investingActivities = List.of();
        BigDecimal netInvestingCashFlow = BigDecimal.ZERO;
        List<CashFlowReport.Line> financingActivities = List.of();
        BigDecimal netFinancingCashFlow = BigDecimal.ZERO;

        BigDecimal netCashFlow = netOperatingCashFlow.add(netInvestingCashFlow).add(netFinancingCashFlow);
        BigDecimal closingCashBalance = openingCashBalance.add(netCashFlow);

        return CashFlowReport.builder()
                .fromDate(fromDate)
                .toDate(toDate)
                .shopId(shopId)
                .openingCashBalance(openingCashBalance)
                .operatingActivities(operatingActivities)
                .netOperatingCashFlow(netOperatingCashFlow)
                .investingActivities(investingActivities)
                .netInvestingCashFlow(netInvestingCashFlow)
                .financingActivities(financingActivities)
                .netFinancingCashFlow(netFinancingCashFlow)
                .netCashFlow(netCashFlow)
                .closingCashBalance(closingCashBalance)
                .reconciled(closingCashBalance.compareTo(actualClosingCashBalance) == 0)
                .build();
    }

    private BigDecimal netCashBalance(List<Object[]> rows, List<Long> cashAccountIds) {
        BigDecimal net = BigDecimal.ZERO;
        for (Object[] row : rows) {
            Long accountId = (Long) row[0];
            if (!cashAccountIds.contains(accountId)) {
                continue;
            }
            BigDecimal debit = (BigDecimal) row[1];
            BigDecimal credit = (BigDecimal) row[2];
            net = net.add(debit).subtract(credit);
        }
        return net;
    }
}
