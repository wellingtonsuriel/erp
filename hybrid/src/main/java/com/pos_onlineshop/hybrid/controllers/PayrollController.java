package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.dtos.*;
import com.pos_onlineshop.hybrid.gl.GLPostingException;
import com.pos_onlineshop.hybrid.services.DeductionTypeService;
import com.pos_onlineshop.hybrid.services.PayrollService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@RestController
@RequestMapping("/api/payroll")
@RequiredArgsConstructor
@Slf4j
public class PayrollController {

    private final PayrollService payrollService;
    private final DeductionTypeService deductionTypeService;

    @GetMapping("/runs")
    @PreAuthorize("hasAuthority('GL_VIEW') or hasRole('ADMIN')")
    public List<PayrollRunResponse> listRuns() {
        return payrollService.findAll();
    }

    @GetMapping("/runs/{id}")
    @PreAuthorize("hasAuthority('GL_VIEW') or hasRole('ADMIN')")
    public ResponseEntity<?> getRun(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(payrollService.findById(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/runs")
    @PreAuthorize("hasAuthority('GL_MANUAL_JOURNAL') or hasRole('ADMIN')")
    public ResponseEntity<?> processRun(@Valid @RequestBody ProcessPayrollRequest request) {
        return runCreate(() -> payrollService.processPayroll(request));
    }

    @PostMapping("/runs/{id}/pay")
    @PreAuthorize("hasAuthority('GL_APPROVE') or hasAuthority('GL_ADMIN') or hasRole('ADMIN')")
    public ResponseEntity<?> payRun(@PathVariable Long id, @Valid @RequestBody PayPayrollRunRequest request) {
        return runTransition(() -> payrollService.payRun(id, request.getUserId()));
    }

    @GetMapping("/deduction-types")
    @PreAuthorize("hasAuthority('GL_VIEW') or hasRole('ADMIN')")
    public List<?> listDeductionTypes() {
        return deductionTypeService.findAllActive();
    }

    @PostMapping("/deduction-types")
    @PreAuthorize("hasAuthority('GL_ADMIN') or hasRole('ADMIN')")
    public ResponseEntity<?> createDeductionType(@Valid @RequestBody CreateDeductionTypeRequest request) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(deductionTypeService.create(request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/deduction-types/{id}/deactivate")
    @PreAuthorize("hasAuthority('GL_ADMIN') or hasRole('ADMIN')")
    public ResponseEntity<?> deactivateDeductionType(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(deductionTypeService.deactivate(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private ResponseEntity<?> runCreate(Supplier<PayrollRunResponse> action) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(action.get());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException | GLPostingException e) {
            log.warn("Invalid payroll processing request: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    private ResponseEntity<?> runTransition(Supplier<PayrollRunResponse> action) {
        try {
            return ResponseEntity.ok(action.get());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException | GLPostingException e) {
            log.warn("Invalid payroll transition: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }
}
