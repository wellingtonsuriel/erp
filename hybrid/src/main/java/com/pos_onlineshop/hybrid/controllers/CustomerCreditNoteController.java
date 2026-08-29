package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.dtos.CreateCustomerCreditNoteRequest;
import com.pos_onlineshop.hybrid.dtos.CustomerCreditNoteResponse;
import com.pos_onlineshop.hybrid.gl.GLPostingException;
import com.pos_onlineshop.hybrid.services.CustomerCreditNoteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/customer-credit-notes")
@RequiredArgsConstructor
@Slf4j
public class CustomerCreditNoteController {

    private final CustomerCreditNoteService customerCreditNoteService;

    @GetMapping
    @PreAuthorize("hasAuthority('AR_VIEW') or hasRole('ADMIN')")
    public List<CustomerCreditNoteResponse> list() {
        return customerCreditNoteService.findAll();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('AR_VIEW') or hasRole('ADMIN')")
    public ResponseEntity<?> get(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(customerCreditNoteService.findById(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping
    @PreAuthorize("hasAuthority('AR_CREATE') or hasRole('ADMIN')")
    public ResponseEntity<?> create(@Valid @RequestBody CreateCustomerCreditNoteRequest request) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(customerCreditNoteService.createCreditNote(request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/post")
    @PreAuthorize("hasAuthority('AR_CREATE') or hasRole('ADMIN')")
    public ResponseEntity<?> post(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(customerCreditNoteService.postCreditNote(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException | GLPostingException e) {
            log.warn("Cannot post credit note {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/void")
    @PreAuthorize("hasAuthority('AR_CREATE') or hasRole('ADMIN')")
    public ResponseEntity<?> voidCreditNote(@PathVariable Long id, @RequestBody Map<String, String> body) {
        try {
            return ResponseEntity.ok(customerCreditNoteService.voidCreditNote(id, body.get("reason")));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }
}
