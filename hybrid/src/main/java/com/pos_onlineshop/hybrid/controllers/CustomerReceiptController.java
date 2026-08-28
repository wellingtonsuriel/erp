package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.customerReceipt.CustomerReceipt;
import com.pos_onlineshop.hybrid.dtos.RecordCustomerReceiptRequest;
import com.pos_onlineshop.hybrid.services.CustomerReceiptService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/customer-receipts")
@RequiredArgsConstructor
@Slf4j
public class CustomerReceiptController {

    private final CustomerReceiptService customerReceiptService;

    @PostMapping
    public ResponseEntity<?> record(@Valid @RequestBody RecordCustomerReceiptRequest request) {
        try {
            CustomerReceipt receipt = customerReceiptService.recordReceipt(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(receipt);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            log.warn("Invalid customer receipt: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }
}
