package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.dtos.*;
import com.pos_onlineshop.hybrid.enums.TransferStatus;
import com.pos_onlineshop.hybrid.inventory.InventoryItem;
import com.pos_onlineshop.hybrid.inventoryTransfer.InventoryTransfer;
import com.pos_onlineshop.hybrid.services.InventoryService;
import com.pos_onlineshop.hybrid.services.InventoryTransferService;
import com.pos_onlineshop.hybrid.services.ShopInventoryService;
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
    private final ShopInventoryService shopInventoryService;
    private final InventoryTransferService inventoryTransferService;

    @GetMapping("/product/{productId}")
    public ResponseEntity<InventoryItem> getInventoryByProduct(@PathVariable Long productId) {
        return inventoryService.findByProductId(productId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get product availability with per-shop stock breakdown from InventoryTotal.
     * Returns total stock across all shops, reserved quantity, and per-shop details.
     */
    @GetMapping("/product/{productId}/availability")
    public ResponseEntity<Map<String, Object>> getProductAvailability(@PathVariable Long productId) {
        log.info("Fetching product availability for product ID: {}", productId);
        Map<String, Object> availability = inventoryService.getProductAvailabilityFromTotal(productId);
        return ResponseEntity.ok(availability);
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

    /**
     * Get low stock items from InventoryTotal with ShopInventory reorder levels.
     * Returns per-shop, per-product low stock entries consistent with report data.
     */
    @GetMapping("/low-stock")
    public ResponseEntity<List<Map<String, Object>>> getLowStockItems() {
        log.info("Fetching low stock items from InventoryTotal");
        List<Map<String, Object>> lowStockItems = inventoryService.findLowStockItemsFromTotal();
        return ResponseEntity.ok(lowStockItems);
    }

    /**
     * Get total inventory value computed from InventoryTotal stock × ShopInventory unit prices.
     * Consistent with the stock value report calculation.
     */
    @GetMapping("/total-value")
    public ResponseEntity<BigDecimal> getTotalInventoryValue() {
        log.info("Calculating total inventory value from InventoryTotal");
        return ResponseEntity.ok(inventoryService.calculateTotalInventoryValueFromTotal());
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

    // ==================== Additional Inventory Report Endpoints ====================

    /**
     * Get all out-of-stock items across all shops from InventoryTotal.
     * Returns shop and product details for items with zero stock.
     */
    @GetMapping("/reports/out-of-stock")
    public ResponseEntity<List<Map<String, Object>>> getOutOfStockItems() {
        log.info("Admin requested out-of-stock items report");
        List<Map<String, Object>> outOfStock = inventoryService.findOutOfStockItemsFromTotal();
        return ResponseEntity.ok(outOfStock);
    }

    /**
     * Get full shop inventory details from InventoryTotal for a specific shop.
     * Returns product-level stock, pricing, and supplier details.
     */
    @GetMapping("/reports/shop/{shopId}/inventory")
    public ResponseEntity<List<ShopInventoryResponse>> getShopInventoryReport(@PathVariable Long shopId) {
        log.info("Admin requested shop inventory report for shop ID: {}", shopId);
        List<ShopInventoryResponse> inventory = shopInventoryService.getShopInventoryFromTotal(shopId);
        return ResponseEntity.ok(inventory);
    }

    /**
     * Get overdue inventory transfers.
     * Returns transfers that are in transit past their expected delivery date.
     */
    @GetMapping("/reports/overdue-transfers")
    public ResponseEntity<List<InventoryTransfer>> getOverdueTransfers() {
        log.info("Admin requested overdue transfers report");
        List<InventoryTransfer> overdueTransfers = inventoryTransferService.findOverdueTransfers();
        return ResponseEntity.ok(overdueTransfers);
    }

    /**
     * Get inventory transfers filtered by status.
     * Useful for reporting on pending, approved, in-transit, or completed transfers.
     */
    @GetMapping("/reports/transfers/status/{status}")
    public ResponseEntity<List<InventoryTransfer>> getTransfersByStatus(@PathVariable TransferStatus status) {
        log.info("Admin requested transfers by status: {}", status);
        List<InventoryTransfer> transfers = inventoryTransferService.findByStatus(status);
        return ResponseEntity.ok(transfers);
    }

    /**
     * Get count of active (pending/approved/in-transit) transfers from a specific shop.
     * Useful for monitoring shop transfer activity.
     */
    @GetMapping("/reports/active-transfers/shop/{shopId}")
    public ResponseEntity<Map<String, Object>> getActiveTransferCountForShop(@PathVariable Long shopId) {
        log.info("Admin requested active transfer count for shop ID: {}", shopId);
        long count = inventoryTransferService.countActiveTransfersFromShop(shopId);
        return ResponseEntity.ok(Map.of(
                "shopId", shopId,
                "activeTransferCount", count
        ));
    }
}
