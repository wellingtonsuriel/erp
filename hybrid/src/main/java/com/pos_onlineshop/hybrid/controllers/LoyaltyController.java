package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.dtos.LoyaltyTransactionRequest;
import com.pos_onlineshop.hybrid.dtos.LoyaltyTransactionResponse;
import com.pos_onlineshop.hybrid.gl.GLPostingException;
import com.pos_onlineshop.hybrid.services.LoyaltyService;
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
@RequestMapping("/api/loyalty")
@RequiredArgsConstructor
@Slf4j
public class LoyaltyController {

    private final LoyaltyService loyaltyService;

    @GetMapping("/accounts/{customerId}")
    @PreAuthorize("hasAuthority('GL_VIEW') or hasRole('ADMIN')")
    public ResponseEntity<?> getAccount(@PathVariable Long customerId) {
        try {
            return ResponseEntity.ok(loyaltyService.getAccount(customerId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/accounts/{customerId}/transactions")
    @PreAuthorize("hasAuthority('GL_VIEW') or hasRole('ADMIN')")
    public ResponseEntity<?> getTransactions(@PathVariable Long customerId) {
        try {
            return ResponseEntity.ok(loyaltyService.getTransactions(customerId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/earn")
    @PreAuthorize("hasAuthority('GL_MANUAL_JOURNAL') or hasRole('ADMIN')")
    public ResponseEntity<?> earn(@Valid @RequestBody LoyaltyTransactionRequest request) {
        return run(() -> loyaltyService.earn(request), HttpStatus.CREATED);
    }

    @PostMapping("/redeem")
    @PreAuthorize("hasAuthority('GL_MANUAL_JOURNAL') or hasRole('ADMIN')")
    public ResponseEntity<?> redeem(@Valid @RequestBody LoyaltyTransactionRequest request) {
        return run(() -> loyaltyService.redeem(request), HttpStatus.CREATED);
    }

    @PostMapping("/expire")
    @PreAuthorize("hasAuthority('GL_MANUAL_JOURNAL') or hasRole('ADMIN')")
    public ResponseEntity<?> expire(@Valid @RequestBody LoyaltyTransactionRequest request) {
        return run(() -> loyaltyService.expire(request), HttpStatus.CREATED);
    }

    @PostMapping("/reverse")
    @PreAuthorize("hasAuthority('GL_MANUAL_JOURNAL') or hasRole('ADMIN')")
    public ResponseEntity<?> reverse(@Valid @RequestBody LoyaltyTransactionRequest request) {
        return run(() -> loyaltyService.reverse(request), HttpStatus.CREATED);
    }

    private ResponseEntity<?> run(Supplier<LoyaltyTransactionResponse> action, HttpStatus successStatus) {
        try {
            return ResponseEntity.status(successStatus).body(action.get());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException | GLPostingException e) {
            log.warn("Invalid loyalty transaction: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }
}
