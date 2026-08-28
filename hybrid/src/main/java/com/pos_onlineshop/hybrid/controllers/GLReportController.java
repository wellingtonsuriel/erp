package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.dtos.TrialBalanceReport;
import com.pos_onlineshop.hybrid.services.TrialBalanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * First endpoint of what the implementation spec calls for as /api/reports/*. Only Trial
 * Balance is implemented this pass - P&L, Balance Sheet, Cash Flow, VAT, GL detail, and aging
 * reports are not (see the implementation summary).
 */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class GLReportController {

    private final TrialBalanceService trialBalanceService;

    @GetMapping("/trial-balance")
    public ResponseEntity<TrialBalanceReport> trialBalance(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) Long shopId) {
        return ResponseEntity.ok(trialBalanceService.generate(fromDate, toDate, shopId));
    }
}
