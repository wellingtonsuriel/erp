package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.dtos.CreateSupplierInvoiceRequest;
import com.pos_onlineshop.hybrid.dtos.SupplierInvoiceResponse;
import com.pos_onlineshop.hybrid.services.SupplierInvoiceService;
import com.pos_onlineshop.hybrid.supplierInvoice.SupplierInvoice;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/supplier-invoices")
@RequiredArgsConstructor
@Slf4j
public class SupplierInvoiceController {

    private final SupplierInvoiceService supplierInvoiceService;

    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody CreateSupplierInvoiceRequest request) {
        try {
            SupplierInvoice invoice = supplierInvoiceService.createInvoice(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(supplierInvoiceService.toResponse(invoice));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping
    public List<SupplierInvoiceResponse> list(@RequestParam(required = false) Boolean outstandingOnly) {
        List<SupplierInvoice> invoices = Boolean.TRUE.equals(outstandingOnly)
                ? supplierInvoiceService.findOutstanding()
                : supplierInvoiceService.findAll();
        return invoices.stream().map(supplierInvoiceService::toResponse).collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(supplierInvoiceService.toResponse(supplierInvoiceService.findOrThrow(id)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{id}/post")
    public ResponseEntity<?> post(@PathVariable Long id) {
        return runTransition(() -> supplierInvoiceService.toResponse(supplierInvoiceService.postInvoice(id)));
    }

    @PostMapping("/{id}/void")
    public ResponseEntity<?> voidInvoice(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.getOrDefault("reason", "Not specified") : "Not specified";
        return runTransition(() -> supplierInvoiceService.toResponse(supplierInvoiceService.voidInvoice(id, reason)));
    }

    private ResponseEntity<?> runTransition(Supplier<SupplierInvoiceResponse> action) {
        try {
            return ResponseEntity.ok(action.get());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            log.warn("Invalid supplier invoice transition: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }
}
