package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.dtos.*;
import com.pos_onlineshop.hybrid.gl.GLPostingException;
import com.pos_onlineshop.hybrid.services.BankAccountService;
import com.pos_onlineshop.hybrid.services.BankChargeService;
import com.pos_onlineshop.hybrid.services.CashBankTransferService;
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
@RequestMapping("/api/cash-bank")
@RequiredArgsConstructor
@Slf4j
public class CashBankController {

    private final BankAccountService bankAccountService;
    private final CashBankTransferService cashBankTransferService;
    private final BankChargeService bankChargeService;

    @GetMapping("/accounts")
    @PreAuthorize("hasAuthority('GL_VIEW') or hasRole('ADMIN')")
    public List<BankAccountResponse> listAccounts() {
        return bankAccountService.findAllActive();
    }

    @GetMapping("/accounts/{id}")
    @PreAuthorize("hasAuthority('GL_VIEW') or hasRole('ADMIN')")
    public ResponseEntity<?> getAccount(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(bankAccountService.findById(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/accounts")
    @PreAuthorize("hasAuthority('GL_ADMIN') or hasRole('ADMIN')")
    public ResponseEntity<?> createAccount(@Valid @RequestBody CreateBankAccountRequest request) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(bankAccountService.create(request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/accounts/{id}/deactivate")
    @PreAuthorize("hasAuthority('GL_ADMIN') or hasRole('ADMIN')")
    public ResponseEntity<?> deactivateAccount(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(bankAccountService.deactivate(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/transfers")
    @PreAuthorize("hasAuthority('GL_VIEW') or hasRole('ADMIN')")
    public List<CashBankTransferResponse> listTransfers() {
        return cashBankTransferService.findAll();
    }

    @PostMapping("/transfers")
    @PreAuthorize("hasAuthority('GL_MANUAL_JOURNAL') or hasRole('ADMIN')")
    public ResponseEntity<?> createTransfer(@Valid @RequestBody CreateCashBankTransferRequest request) {
        return runCreate(() -> cashBankTransferService.createTransfer(request));
    }

    @GetMapping("/charges")
    @PreAuthorize("hasAuthority('GL_VIEW') or hasRole('ADMIN')")
    public List<BankChargeResponse> listCharges() {
        return bankChargeService.findAll();
    }

    @PostMapping("/charges")
    @PreAuthorize("hasAuthority('GL_MANUAL_JOURNAL') or hasRole('ADMIN')")
    public ResponseEntity<?> createCharge(@Valid @RequestBody CreateBankChargeRequest request) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(bankChargeService.createCharge(request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException | GLPostingException e) {
            log.warn("Invalid bank charge: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    private ResponseEntity<?> runCreate(Supplier<CashBankTransferResponse> action) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(action.get());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException | GLPostingException e) {
            log.warn("Invalid cash/bank transfer: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }
}
