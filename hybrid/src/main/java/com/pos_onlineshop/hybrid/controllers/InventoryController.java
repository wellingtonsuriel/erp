package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.dtos.ReorderLevelRequest;
import com.pos_onlineshop.hybrid.dtos.StockUpdateRequest;
import com.pos_onlineshop.hybrid.inventory.InventoryItem;
import com.pos_onlineshop.hybrid.services.InventoryService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@CrossOrigin(origins = "*")
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping("/product/{productId}")
    public ResponseEntity<InventoryItem> getInventoryByProduct(@PathVariable Long productId) {
        return inventoryService.findByProductId(productId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/product/{productId}/availability")
    public ResponseEntity<Map<String, Integer>> getProductAvailability(@PathVariable Long productId) {
        return ResponseEntity.ok(inventoryService.getChannelInventory(productId));
    }

    @PostMapping("/product/{productId}/add")
    public ResponseEntity<Void> addStock(
            @PathVariable Long productId,
            @RequestBody StockUpdateRequest request) {
        inventoryService.addStock(productId, request.getQuantity());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/product/{productId}/remove")
    public ResponseEntity<Void> removeStock(
            @PathVariable Long productId,
            @RequestBody StockUpdateRequest request) {
        try {
            inventoryService.removeStock(productId, request.getQuantity());
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/product/{productId}/reserve")
    public ResponseEntity<Void> reserveStock(
            @PathVariable Long productId,
            @RequestBody StockUpdateRequest request) {
        try {
            inventoryService.reserveInventory(productId, request.getQuantity());
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/product/{productId}/release")
    public ResponseEntity<Void> releaseReservation(
            @PathVariable Long productId,
            @RequestBody StockUpdateRequest request) {
        inventoryService.releaseReservation(productId, request.getQuantity());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/check/{productId}")
    public ResponseEntity<Boolean> checkStock(
            @PathVariable Long productId,
            @RequestParam Integer quantity) {
        boolean inStock = inventoryService.isInStock(productId, quantity);
        return ResponseEntity.ok(inStock);
    }

    @GetMapping("/low-stock")
    public List<InventoryItem> getLowStockItems() {
        return inventoryService.findLowStockItems();
    }

    @GetMapping("/total-value")
    public ResponseEntity<BigDecimal> getTotalInventoryValue() {
        return ResponseEntity.ok(inventoryService.calculateTotalInventoryValue());
    }

    @PutMapping("/product/{productId}/reorder-level")
    public ResponseEntity<Void> updateReorderLevel(
            @PathVariable Long productId,
            @RequestBody ReorderLevelRequest request) {
        inventoryService.updateReorderLevel(productId, request.getReorderLevel());
        return ResponseEntity.ok().build();
    }


}