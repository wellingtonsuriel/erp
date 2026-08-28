package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.dtos.CreatePurchaseOrderRequest;
import com.pos_onlineshop.hybrid.dtos.PurchaseOrderResponse;
import com.pos_onlineshop.hybrid.dtos.ReceivePurchaseOrderRequest;
import com.pos_onlineshop.hybrid.purchaseOrder.PurchaseOrder;
import com.pos_onlineshop.hybrid.services.PurchaseOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/purchase-orders")
@RequiredArgsConstructor
@Slf4j
public class PurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;

    @PostMapping
    public ResponseEntity<PurchaseOrderResponse> create(@Valid @RequestBody CreatePurchaseOrderRequest request) {
        try {
            PurchaseOrder po = purchaseOrderService.createPurchaseOrder(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(purchaseOrderService.toResponse(po));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(null);
        }
    }

    @GetMapping
    public List<PurchaseOrderResponse> list() {
        return purchaseOrderService.findAll().stream()
                .map(purchaseOrderService::toResponse)
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PurchaseOrderResponse> get(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(purchaseOrderService.toResponse(purchaseOrderService.findOrThrow(id)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{id}/submit")
    public ResponseEntity<?> submit(@PathVariable Long id) {
        return runTransition(() -> purchaseOrderService.toResponse(purchaseOrderService.submit(id)));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<?> approve(@PathVariable Long id, @RequestBody Map<String, Long> body) {
        Long approverId = body.get("approverId");
        if (approverId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "approverId is required"));
        }
        return runTransition(() -> purchaseOrderService.toResponse(purchaseOrderService.approve(id, approverId)));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<?> cancel(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.getOrDefault("reason", "Not specified") : "Not specified";
        return runTransition(() -> purchaseOrderService.toResponse(purchaseOrderService.cancel(id, reason)));
    }

    @PostMapping("/{id}/close")
    public ResponseEntity<?> close(@PathVariable Long id) {
        return runTransition(() -> purchaseOrderService.toResponse(purchaseOrderService.close(id)));
    }

    @PostMapping("/{id}/receive")
    public ResponseEntity<?> receive(@PathVariable Long id, @Valid @RequestBody ReceivePurchaseOrderRequest request) {
        return runTransition(() -> purchaseOrderService.toResponse(purchaseOrderService.receive(id, request)));
    }

    private ResponseEntity<?> runTransition(java.util.function.Supplier<PurchaseOrderResponse> action) {
        try {
            return ResponseEntity.ok(action.get());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            log.warn("Invalid purchase order transition: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }
}
