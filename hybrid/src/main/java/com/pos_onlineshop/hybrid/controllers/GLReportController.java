package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.dtos.AccountLedgerReport;
import com.pos_onlineshop.hybrid.dtos.ApAgingReport;
import com.pos_onlineshop.hybrid.dtos.ArAgingReport;
import com.pos_onlineshop.hybrid.dtos.BalanceSheetReport;
import com.pos_onlineshop.hybrid.dtos.CashFlowReport;
import com.pos_onlineshop.hybrid.dtos.ControlAccountReconciliationReport;
import com.pos_onlineshop.hybrid.dtos.CustomerStatementReport;
import com.pos_onlineshop.hybrid.dtos.LegacyGlReconciliationReport;
import com.pos_onlineshop.hybrid.dtos.ProfitAndLossReport;
import com.pos_onlineshop.hybrid.dtos.SupplierStatementReport;
import com.pos_onlineshop.hybrid.dtos.TrialBalanceReport;
import com.pos_onlineshop.hybrid.dtos.VatReturnReport;
import com.pos_onlineshop.hybrid.services.ApAgingService;
import com.pos_onlineshop.hybrid.services.ArAgingService;
import com.pos_onlineshop.hybrid.services.BalanceSheetService;
import com.pos_onlineshop.hybrid.services.CashFlowService;
import com.pos_onlineshop.hybrid.services.ControlAccountReconciliationService;
import com.pos_onlineshop.hybrid.services.CustomerStatementService;
import com.pos_onlineshop.hybrid.services.GeneralLedgerService;
import com.pos_onlineshop.hybrid.services.LegacyGlReconciliationService;
import com.pos_onlineshop.hybrid.services.ProfitAndLossService;
import com.pos_onlineshop.hybrid.services.SupplierStatementService;
import com.pos_onlineshop.hybrid.services.TrialBalanceService;
import com.pos_onlineshop.hybrid.services.VatReturnService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * /api/reports/*. Implemented: Trial Balance, AP aging, AR aging, Profit & Loss, Balance
 * Sheet, VAT Return, Cash Flow, control-account reconciliation, customer/supplier
 * statements, and the account-ledger GL detail drill-down (one account's individual posted
 * lines with a running balance - see GeneralLedgerService).
 */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('REPORT_VIEW') or hasRole('ADMIN')")
public class GLReportController {

    private final TrialBalanceService trialBalanceService;
    private final ApAgingService apAgingService;
    private final ArAgingService arAgingService;
    private final ProfitAndLossService profitAndLossService;
    private final BalanceSheetService balanceSheetService;
    private final VatReturnService vatReturnService;
    private final CashFlowService cashFlowService;
    private final ControlAccountReconciliationService controlAccountReconciliationService;
    private final LegacyGlReconciliationService legacyGlReconciliationService;
    private final CustomerStatementService customerStatementService;
    private final SupplierStatementService supplierStatementService;
    private final GeneralLedgerService generalLedgerService;

    @GetMapping("/trial-balance")
    public ResponseEntity<TrialBalanceReport> trialBalance(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) Long shopId) {
        return ResponseEntity.ok(trialBalanceService.generate(fromDate, toDate, shopId));
    }

    @GetMapping("/ap-aging")
    public ResponseEntity<ApAgingReport> apAging(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate) {
        return ResponseEntity.ok(apAgingService.generate(asOfDate != null ? asOfDate : LocalDate.now()));
    }

    @GetMapping("/ar-aging")
    public ResponseEntity<ArAgingReport> arAging(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate) {
        return ResponseEntity.ok(arAgingService.generate(asOfDate != null ? asOfDate : LocalDate.now()));
    }

    @GetMapping("/profit-and-loss")
    public ResponseEntity<ProfitAndLossReport> profitAndLoss(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) Long shopId) {
        return ResponseEntity.ok(profitAndLossService.generate(fromDate, toDate, shopId));
    }

    @GetMapping("/balance-sheet")
    public ResponseEntity<BalanceSheetReport> balanceSheet(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate,
            @RequestParam(required = false) Long shopId) {
        return ResponseEntity.ok(balanceSheetService.generate(asOfDate != null ? asOfDate : LocalDate.now(), shopId));
    }

    @GetMapping("/vat-return")
    public ResponseEntity<VatReturnReport> vatReturn(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) Long shopId) {
        return ResponseEntity.ok(vatReturnService.generate(fromDate, toDate, shopId));
    }

    @GetMapping("/cash-flow")
    public ResponseEntity<CashFlowReport> cashFlow(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) Long shopId) {
        return ResponseEntity.ok(cashFlowService.generate(fromDate, toDate, shopId));
    }

    @GetMapping("/reconciliation")
    public ResponseEntity<ControlAccountReconciliationReport> reconciliation(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate) {
        return ResponseEntity.ok(controlAccountReconciliationService.generate(asOfDate != null ? asOfDate : LocalDate.now()));
    }

    @GetMapping("/legacy-reconciliation")
    public ResponseEntity<LegacyGlReconciliationReport> legacyReconciliation(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return ResponseEntity.ok(legacyGlReconciliationService.generate(fromDate, toDate));
    }

    @GetMapping("/customer-statement")
    public ResponseEntity<?> customerStatement(
            @RequestParam Long customerId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        try {
            return ResponseEntity.ok(customerStatementService.generate(customerId, fromDate, toDate));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/supplier-statement")
    public ResponseEntity<?> supplierStatement(
            @RequestParam Long supplierId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        try {
            return ResponseEntity.ok(supplierStatementService.generate(supplierId, fromDate, toDate));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/account-ledger")
    public ResponseEntity<?> accountLedger(
            @RequestParam Long accountId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) Long shopId) {
        try {
            return ResponseEntity.ok(generalLedgerService.generateAccountLedger(accountId, fromDate, toDate, shopId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
