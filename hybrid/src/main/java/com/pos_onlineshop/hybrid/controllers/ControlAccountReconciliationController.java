package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.dtos.ControlAccountReconciliationRunResponse;
import com.pos_onlineshop.hybrid.dtos.ResolveReconciliationLineRequest;
import com.pos_onlineshop.hybrid.services.ControlAccountReconciliationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * run/resolveLine's performedBy/resolvedBy must always be the authenticated caller's username,
 * never a request-body field - previously any GL_ADMIN-authorized caller could claim any string,
 * including someone else's name, as who ran a reconciliation or resolved a line. The same class
 * of audit-attribution forgery fixed on AccountingPeriodController's close/reopen.
 */
@RestController
@RequestMapping("/api/control-account-reconciliations")
@RequiredArgsConstructor
@Slf4j
public class ControlAccountReconciliationController {

    private final ControlAccountReconciliationService controlAccountReconciliationService;

    @GetMapping
    @PreAuthorize("hasAuthority('GL_VIEW') or hasRole('ADMIN')")
    public List<ControlAccountReconciliationRunResponse> list() {
        return controlAccountReconciliationService.findAllRuns();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('GL_VIEW') or hasRole('ADMIN')")
    public ResponseEntity<?> get(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(controlAccountReconciliationService.findRunById(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/run")
    @PreAuthorize("hasAuthority('GL_ADMIN') or hasRole('ADMIN')")
    public ResponseEntity<?> run(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate,
            @AuthenticationPrincipal UserDetails principal) {
        LocalDate date = asOfDate != null ? asOfDate : LocalDate.now();
        return ResponseEntity.status(HttpStatus.CREATED).body(controlAccountReconciliationService.runAndPersist(date, principal.getUsername()));
    }

    @PostMapping("/lines/{lineId}/resolve")
    @PreAuthorize("hasAuthority('GL_ADMIN') or hasRole('ADMIN')")
    public ResponseEntity<?> resolveLine(@PathVariable Long lineId, @Valid @RequestBody ResolveReconciliationLineRequest request,
                                          @AuthenticationPrincipal UserDetails principal) {
        try {
            return ResponseEntity.ok(controlAccountReconciliationService.resolveLine(
                    lineId, request.getResolutionReason(), principal.getUsername()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            log.warn("Invalid reconciliation line resolution: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }
}
