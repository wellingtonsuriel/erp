package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.dtos.*;
import com.pos_onlineshop.hybrid.services.InventoryService;
import com.pos_onlineshop.hybrid.services.InventoryValuationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * The product/{id}, availability, add/remove/reserve/release, check, low-stock, and
 * reorder-level endpoints that used to live here were removed: they operated on the
 * deprecated global InventoryItem pool, which is never populated by normal operation (see
 * InventoryService's class comment) - the endpoints always returned 404/empty/no-op results.
 * That pool's underlying InventoryService methods are still used internally, but only as a
 * narrow legacy fallback for orders placed before shop-scoped inventory existed (see
 * OrderService.legacyConfirmWithoutShop/legacyRestoreWithoutShop) - never as a general-purpose
 * inventory API. The real, shop-scoped, authoritative equivalents are ShopInventoryController
 * (read/adjust a specific shop's InventoryTotal-backed record) and InventoryAdjustmentController
 * (GL-integrated stock-count corrections); real-time balances/valuation/movements live below and
 * in InventoryReportController.
 */
@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@CrossOrigin(origins = "*")
@Slf4j
public class InventoryController {

    private final InventoryService inventoryService;
    private final InventoryValuationService inventoryValuationService;

    /**
     * The real FIFO cost-layer total, not the deprecated InventoryItem pool's figure (which is
     * always ~zero since that pool is never populated in normal operation - see
     * InventoryService.calculateTotalInventoryValue's deprecation note).
     */
    @GetMapping("/total-value")
    public ResponseEntity<BigDecimal> getTotalInventoryValue() {
        return ResponseEntity.ok(inventoryValuationService.getTotalInventoryValue());
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
