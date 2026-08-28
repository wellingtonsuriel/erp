package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.dtos.RecordSupplierPaymentRequest;
import com.pos_onlineshop.hybrid.services.SupplierPaymentService;
import com.pos_onlineshop.hybrid.supplierPayment.SupplierPayment;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/supplier-payments")
@RequiredArgsConstructor
@Slf4j
public class SupplierPaymentController {

    private final SupplierPaymentService supplierPaymentService;

    @PostMapping
    public ResponseEntity<?> record(@Valid @RequestBody RecordSupplierPaymentRequest request) {
        try {
            SupplierPayment payment = supplierPaymentService.recordPayment(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(payment);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            log.warn("Invalid supplier payment: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }
}
