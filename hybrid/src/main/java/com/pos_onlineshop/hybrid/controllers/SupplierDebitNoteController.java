package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.dtos.CreateSupplierDebitNoteRequest;
import com.pos_onlineshop.hybrid.dtos.SupplierDebitNoteResponse;
import com.pos_onlineshop.hybrid.gl.GLPostingException;
import com.pos_onlineshop.hybrid.services.SupplierDebitNoteService;
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
@RequestMapping("/api/supplier-debit-notes")
@RequiredArgsConstructor
@Slf4j
public class SupplierDebitNoteController {

    private final SupplierDebitNoteService supplierDebitNoteService;

    @GetMapping
    @PreAuthorize("hasAuthority('AP_VIEW') or hasRole('ADMIN')")
    public List<SupplierDebitNoteResponse> list() {
        return supplierDebitNoteService.findAll();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('AP_VIEW') or hasRole('ADMIN')")
    public ResponseEntity<?> get(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(supplierDebitNoteService.findById(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping
    @PreAuthorize("hasAuthority('AP_CREATE') or hasRole('ADMIN')")
    public ResponseEntity<?> create(@Valid @RequestBody CreateSupplierDebitNoteRequest request) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(supplierDebitNoteService.createDebitNote(request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/post")
    @PreAuthorize("hasAuthority('AP_CREATE') or hasRole('ADMIN')")
    public ResponseEntity<?> post(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(supplierDebitNoteService.postDebitNote(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException | GLPostingException e) {
            log.warn("Cannot post debit note {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/void")
    @PreAuthorize("hasAuthority('AP_CREATE') or hasRole('ADMIN')")
    public ResponseEntity<?> voidDebitNote(@PathVariable Long id, @RequestBody Map<String, String> body) {
        try {
            return ResponseEntity.ok(supplierDebitNoteService.voidDebitNote(id, body.get("reason")));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }
}
