package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.account.Account;
import com.pos_onlineshop.hybrid.account.AccountRepository;
import com.pos_onlineshop.hybrid.customerInvoice.CustomerInvoice;
import com.pos_onlineshop.hybrid.customerInvoice.CustomerInvoiceRepository;
import com.pos_onlineshop.hybrid.dtos.ControlAccountReconciliationReport;
import com.pos_onlineshop.hybrid.enums.CustomerInvoiceStatus;
import com.pos_onlineshop.hybrid.enums.SupplierInvoiceStatus;
import com.pos_onlineshop.hybrid.journalLine.JournalLineRepository;
import com.pos_onlineshop.hybrid.supplierInvoice.SupplierInvoice;
import com.pos_onlineshop.hybrid.supplierInvoice.SupplierInvoiceRepository;
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
 * Compares each control account's GL balance to its operational subledger - Phase J. Reads
 * exclusively from JournalLine for the GL side (cumulative from inception through asOfDate,
 * same aggregateBeforeDate pattern BalanceSheetService/VatReturnService use), never from
 * Order/OrderLine/ShopInventory reconstructed as if it were the GL.
 */
@Service
@RequiredArgsConstructor
public class ControlAccountReconciliationService {

    private static final String ACCOUNTS_PAYABLE_CODE = "2100";
    private static final String ACCOUNTS_RECEIVABLE_CODE = "1100";
    private static final String INVENTORY_ASSET_CODE = "1200";

    private final AccountRepository accountRepository;
    private final JournalLineRepository journalLineRepository;
    private final SupplierInvoiceRepository supplierInvoiceRepository;
    private final CustomerInvoiceRepository customerInvoiceRepository;
    private final ShopInventoryService shopInventoryService;

    private record Totals(BigDecimal debit, BigDecimal credit) {
        static final Totals ZERO = new Totals(BigDecimal.ZERO, BigDecimal.ZERO);
    }

    @Transactional(readOnly = true)
    public ControlAccountReconciliationReport generate(LocalDate asOfDate) {
        Map<Long, Totals> cumulative = toTotalsMap(journalLineRepository.aggregateBeforeDate(asOfDate.plusDays(1), null));

        List<ControlAccountReconciliationReport.Line> lines = new ArrayList<>();
        lines.add(reconcileAccountsPayable(cumulative));
        lines.add(reconcileAccountsReceivable(cumulative));
        lines.add(reconcileInventory(cumulative));

        return ControlAccountReconciliationReport.builder()
                .asOfDate(asOfDate)
                .lines(lines)
                .build();
    }

    private ControlAccountReconciliationReport.Line reconcileAccountsPayable(Map<Long, Totals> cumulative) {
        Account account = accountByCode(ACCOUNTS_PAYABLE_CODE);
        Totals t = cumulative.getOrDefault(account.getId(), Totals.ZERO);
        BigDecimal glBalance = t.credit().subtract(t.debit()); // 2100 is credit-normal (LIABILITY)

        BigDecimal subledgerBalance = supplierInvoiceRepository
                .findByStatusIn(List.of(SupplierInvoiceStatus.POSTED, SupplierInvoiceStatus.PARTIALLY_PAID)).stream()
                .map(SupplierInvoice::getOutstandingAmount)
                .filter(amount -> amount.compareTo(BigDecimal.ZERO) > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return line(account, "Supplier invoice subledger (outstanding POSTED/PARTIALLY_PAID invoices)",
                glBalance, subledgerBalance, null);
    }

    private ControlAccountReconciliationReport.Line reconcileAccountsReceivable(Map<Long, Totals> cumulative) {
        Account account = accountByCode(ACCOUNTS_RECEIVABLE_CODE);
        Totals t = cumulative.getOrDefault(account.getId(), Totals.ZERO);
        BigDecimal glBalance = t.debit().subtract(t.credit()); // 1100 is debit-normal (ASSET)

        BigDecimal subledgerBalance = customerInvoiceRepository
                .findByStatusIn(List.of(CustomerInvoiceStatus.POSTED, CustomerInvoiceStatus.PARTIALLY_PAID)).stream()
                .map(CustomerInvoice::getOutstandingAmount)
                .filter(amount -> amount.compareTo(BigDecimal.ZERO) > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return line(account, "Customer invoice subledger (outstanding POSTED/PARTIALLY_PAID invoices)",
                glBalance, subledgerBalance, null);
    }

    /**
     * Uses ShopInventoryService.calculateTotalInventoryValue() - the single authoritative
     * inventory valuation, also used by AnalyticsController's dashboard - rather than
     * recomputing it here. Previously this valued immutable ShopInventory receipt-lot
     * quantities directly, which never decrease as stock sells; that overstated the
     * subledger balance against every unit ever sold. It now correctly values the live
     * InventoryTotal on-hand balance at each pair's latest received lot cost.
     */
    private ControlAccountReconciliationReport.Line reconcileInventory(Map<Long, Totals> cumulative) {
        Account account = accountByCode(INVENTORY_ASSET_CODE);
        Totals t = cumulative.getOrDefault(account.getId(), Totals.ZERO);
        BigDecimal glBalance = t.debit().subtract(t.credit()); // 1200 is debit-normal (ASSET)

        BigDecimal subledgerBalance = shopInventoryService.calculateTotalInventoryValue();

        return line(account, "InventoryTotal live on-hand balance (valued at latest lot unit cost)",
                glBalance, subledgerBalance,
                "Inventory subledger is always a live on-hand snapshot, not as-of-date - "
                        + "InventoryTotal has no historical point-in-time query, unlike the GL side.");
    }

    private Account accountByCode(String code) {
        return accountRepository.findByCode(code)
                .orElseThrow(() -> new IllegalStateException("Chart of accounts is missing " + code));
    }

    private ControlAccountReconciliationReport.Line line(Account account, String subledgerName,
                                                           BigDecimal glBalance, BigDecimal subledgerBalance, String note) {
        BigDecimal variance = glBalance.subtract(subledgerBalance);
        return ControlAccountReconciliationReport.Line.builder()
                .accountCode(account.getCode())
                .accountName(account.getName())
                .subledgerName(subledgerName)
                .glBalance(glBalance)
                .subledgerBalance(subledgerBalance)
                .variance(variance)
                .matched(variance.compareTo(BigDecimal.ZERO) == 0)
                .note(note)
                .build();
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
