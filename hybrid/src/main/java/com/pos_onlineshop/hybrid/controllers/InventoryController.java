package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.dtos.*;
import com.pos_onlineshop.hybrid.inventory.InventoryItem;
import com.pos_onlineshop.hybrid.services.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@CrossOrigin(origins = "*")
@Slf4j
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

    // ==================== Stock Reporting Endpoints ====================

    /**
     * Generate a global stock summary report across all shops.
     * Returns total products, stock units, value, and per-shop breakdown.
     */
    @GetMapping("/reports/stock-summary")
    public ResponseEntity<StockSummaryReport> getStockSummaryReport() {
        log.info("Admin requested global stock summary report");
        StockSummaryReport report = inventoryService.generateStockSummaryReport();
        return ResponseEntity.ok(report);
    }

    /**
     * Generate an inventory transfer report.
     * Optionally filter by date range with startDate and endDate query parameters.
     * Returns transfer counts by status/type, value totals, and individual transfer details.
     */
    @GetMapping("/reports/transfers")
    public ResponseEntity<TransferReport> getTransferReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        log.info("Admin requested transfer report: startDate={}, endDate={}", startDate, endDate);
        TransferReport report = inventoryService.generateTransferReport(startDate, endDate);
        return ResponseEntity.ok(report);
    }

    /**
     * Generate a detailed stock report for a specific shop.
     * Lists all products with stock levels, values, reorder levels, and stock status.
     */
    @GetMapping("/reports/shop/{shopId}/stock")
    public ResponseEntity<ShopStockReport> getShopStockReport(@PathVariable Long shopId) {
        log.info("Admin requested shop stock report for shop ID: {}", shopId);
        try {
            ShopStockReport report = inventoryService.generateShopStockReport(shopId);
            return ResponseEntity.ok(report);
        } catch (RuntimeException e) {
            log.error("Error generating shop stock report for shop {}: {}", shopId, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Generate a global stock value report with breakdowns by shop and product category.
     * Provides financial valuation of all inventory with percentage distributions.
     */
    @GetMapping("/reports/stock-value")
    public ResponseEntity<StockValueReport> getStockValueReport() {
        log.info("Admin requested global stock value report");
        StockValueReport report = inventoryService.generateStockValueReport();
        return ResponseEntity.ok(report);
    }
}
