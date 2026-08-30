package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.dtos.AccountingDashboardResponse;
import com.pos_onlineshop.hybrid.services.AccountingDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/accounting-dashboard")
@RequiredArgsConstructor
public class AccountingDashboardController {

    private final AccountingDashboardService accountingDashboardService;

    @GetMapping
    @PreAuthorize("hasAuthority('GL_VIEW') or hasAuthority('REPORT_VIEW') or hasRole('ADMIN')")
    public AccountingDashboardResponse getSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate) {
        return accountingDashboardService.getSummary(asOfDate);
    }
}
