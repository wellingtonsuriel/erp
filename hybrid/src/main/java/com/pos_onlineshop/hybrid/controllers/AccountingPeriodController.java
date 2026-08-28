package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.accountingPeriod.AccountingPeriod;
import com.pos_onlineshop.hybrid.services.AccountingPeriodService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/accounting-periods")
@RequiredArgsConstructor
@Slf4j
public class AccountingPeriodController {

    private final AccountingPeriodService accountingPeriodService;

    @GetMapping
    @PreAuthorize("hasAuthority('GL_VIEW') or hasRole('ADMIN')")
    public List<AccountingPeriod> list() {
        return accountingPeriodService.findAll();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('GL_VIEW') or hasRole('ADMIN')")
    public ResponseEntity<?> get(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(accountingPeriodService.findById(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/ensure")
    @PreAuthorize("hasAuthority('GL_ADMIN') or hasRole('ADMIN')")
    public ResponseEntity<AccountingPeriod> ensure(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.status(HttpStatus.CREATED).body(accountingPeriodService.getOrCreateMonthlyPeriod(date));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAuthority('PERIOD_CLOSE') or hasRole('ADMIN')")
    public ResponseEntity<?> close(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        String closedBy = body != null ? body.getOrDefault("closedBy", "system") : "system";
        try {
            return ResponseEntity.ok(accountingPeriodService.closePeriod(id, closedBy));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            log.warn("Cannot close period {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/reopen")
    @PreAuthorize("hasAuthority('PERIOD_REOPEN') or hasRole('ADMIN')")
    public ResponseEntity<?> reopen(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(accountingPeriodService.reopenPeriod(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            log.warn("Cannot reopen period {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }
}
