package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.dtos.ApAgingReport;
import com.pos_onlineshop.hybrid.dtos.ArAgingReport;
import com.pos_onlineshop.hybrid.dtos.ProfitAndLossReport;
import com.pos_onlineshop.hybrid.dtos.TrialBalanceReport;
import com.pos_onlineshop.hybrid.services.ApAgingService;
import com.pos_onlineshop.hybrid.services.ArAgingService;
import com.pos_onlineshop.hybrid.services.ProfitAndLossService;
import com.pos_onlineshop.hybrid.services.TrialBalanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * /api/reports/*. Implemented: Trial Balance, AP aging, AR aging, Profit & Loss. Not yet
 * implemented: Balance Sheet, Cash Flow, VAT, GL detail (see the implementation summary).
 */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class GLReportController {

    private final TrialBalanceService trialBalanceService;
    private final ApAgingService apAgingService;
    private final ArAgingService arAgingService;
    private final ProfitAndLossService profitAndLossService;

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
}
