package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.customerInvoice.CustomerInvoice;
import com.pos_onlineshop.hybrid.dtos.CreateCustomerInvoiceRequest;
import com.pos_onlineshop.hybrid.dtos.CustomerInvoiceResponse;
import com.pos_onlineshop.hybrid.services.CustomerInvoiceService;
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
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/customer-invoices")
@RequiredArgsConstructor
@Slf4j
public class CustomerInvoiceController {

    private final CustomerInvoiceService customerInvoiceService;

    @PostMapping
    @PreAuthorize("hasAuthority('AR_CREATE') or hasRole('ADMIN')")
    public ResponseEntity<?> create(@Valid @RequestBody CreateCustomerInvoiceRequest request) {
        try {
            CustomerInvoice invoice = customerInvoiceService.createInvoice(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(customerInvoiceService.toResponse(invoice));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping
    @PreAuthorize("hasAuthority('AR_VIEW') or hasRole('ADMIN')")
    public List<CustomerInvoiceResponse> list(@RequestParam(required = false) Boolean outstandingOnly) {
        List<CustomerInvoice> invoices = Boolean.TRUE.equals(outstandingOnly)
                ? customerInvoiceService.findOutstanding()
                : customerInvoiceService.findAll();
        return invoices.stream().map(customerInvoiceService::toResponse).collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('AR_VIEW') or hasRole('ADMIN')")
    public ResponseEntity<?> get(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(customerInvoiceService.toResponse(customerInvoiceService.findOrThrow(id)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{id}/post")
    @PreAuthorize("hasAuthority('AR_CREATE') or hasRole('ADMIN')")
    public ResponseEntity<?> post(@PathVariable Long id) {
        return runTransition(() -> customerInvoiceService.toResponse(customerInvoiceService.postInvoice(id)));
    }

    @PostMapping("/{id}/void")
    @PreAuthorize("hasAuthority('AR_CREATE') or hasRole('ADMIN')")
    public ResponseEntity<?> voidInvoice(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.getOrDefault("reason", "Not specified") : "Not specified";
        return runTransition(() -> customerInvoiceService.toResponse(customerInvoiceService.voidInvoice(id, reason)));
    }

    private ResponseEntity<?> runTransition(Supplier<CustomerInvoiceResponse> action) {
        try {
            return ResponseEntity.ok(action.get());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            log.warn("Invalid customer invoice transition: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }
}
