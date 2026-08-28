package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.dtos.ApAgingReport;
import com.pos_onlineshop.hybrid.dtos.TrialBalanceReport;
import com.pos_onlineshop.hybrid.services.ApAgingService;
import com.pos_onlineshop.hybrid.services.TrialBalanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * /api/reports/*. Implemented: Trial Balance, AP aging. Not yet implemented: P&L, Balance
 * Sheet, Cash Flow, VAT, GL detail, AR aging (see the implementation summary).
 */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class GLReportController {

    private final TrialBalanceService trialBalanceService;
    private final ApAgingService apAgingService;

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
}
