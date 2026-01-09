package com.pos_onlineshop.hybrid.controllers;



import com.pos_onlineshop.hybrid.dtos.*;
import com.pos_onlineshop.hybrid.products.Product;
import com.pos_onlineshop.hybrid.products.ProductRepository;
import com.pos_onlineshop.hybrid.services.ShopInventoryService;
import com.pos_onlineshop.hybrid.shop.Shop;
import com.pos_onlineshop.hybrid.shop.ShopRepository;
import com.pos_onlineshop.hybrid.shopInventory.ShopInventory;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/shop-inventory")
@RequiredArgsConstructor
@Slf4j
public class ShopInventoryController {

    private final ShopInventoryService shopInventoryService;
    private final ShopRepository shopRepository;
    private final ProductRepository productRepository;

    /**
     * Get inventory for a specific shop and product
     */
    @GetMapping("/shop/{shopId}/product/{productId}")
    public ResponseEntity<ShopInventoryResponse> getInventory(
            @PathVariable Long shopId,
            @PathVariable Long productId) {

        Optional<Shop> shop = shopRepository.findById(shopId);
        Optional<Product> product = productRepository.findById(productId);

        if (shop.isEmpty() || product.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Optional<ShopInventory> inventory = shopInventoryService.getInventory(shop.get(), product.get());
        return inventory.map(inv -> ResponseEntity.ok(shopInventoryService.toResponse(inv)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get all inventory items for a shop
     */
    @GetMapping("/shop/{shopId}")
    public ResponseEntity<List<ShopInventoryResponse>> getShopInventory(@PathVariable Long shopId) {
        Optional<Shop> shop = shopRepository.findById(shopId);

        if (shop.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        List<ShopInventory> inventories = shopInventoryService.getShopInventory(shop.get());
        List<ShopInventoryResponse> responses = shopInventoryService.toResponseList(inventories);
        return ResponseEntity.ok(responses);
    }

    /**
     * Get all inventory items for a product across all shops
     */
    @GetMapping("/product/{productId}")
    public ResponseEntity<List<ShopInventoryResponse>> getProductInventory(@PathVariable Long productId) {
        Optional<Product> product = productRepository.findById(productId);

        if (product.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        List<ShopInventory> inventories = shopInventoryService.getProductInventory(product.get());
        List<ShopInventoryResponse> responses = shopInventoryService.toResponseList(inventories);
        return ResponseEntity.ok(responses);
    }

    /**
     * Create inventory (CREATE ONLY - legacy endpoint)
     *
     * IMPORTANT: This endpoint now ONLY creates new inventory records.
     * - Returns 400 if inventory already exists
     * - Use POST /api/shop-inventory with CreateShopInventoryRequest for new creation
     * - Use PATCH /api/shop-inventory/shop/{shopId}/product/{productId} to update metadata
     * - Use POST /api/shop-inventory/shop/{shopId}/product/{productId}/add-stock to add stock
     *
     * @deprecated Use POST /api/shop-inventory instead for better validation
     */
    @PostMapping("/shop/{shopId}/product/{productId}")
    public ResponseEntity<ShopInventoryResponse> createOrUpdateInventory(
            @PathVariable Long shopId,
            @PathVariable Long productId,
            @RequestBody InventoryRequest request) {

        Optional<Shop> shop = shopRepository.findById(shopId);
        Optional<Product> product = productRepository.findById(productId);

        if (shop.isEmpty() || product.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        try {
            ShopInventory inventory = shopInventoryService.createOrUpdateInventory(
                    shop.get(), product.get(), request.getQuantity(),
                    request.getInTransitQuantity(), request.getSupplierId(),
                    request.getCurrencyId(), request.getUnitPrice(),
                    request.getExpiryDate(), request.getReorderLevel(),
                    request.getMinStock(), request.getMaxStock());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(shopInventoryService.toResponse(inventory));
        } catch (RuntimeException e) {
            log.error("Error creating inventory: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error creating inventory", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Add stock to existing inventory (WORLD-CLASS IMPLEMENTATION)
     *
     * Features:
     * - Thread-safe with pessimistic locking
     * - Validates maxStock limits before adding
     * - Updates quantity (current available stock)
     * - Increments totalStock (lifetime cumulative additions for audit trail)
     * - Returns 400 if inventory doesn't exist or would exceed maxStock
     */
    @PostMapping("/shop/{shopId}/product/{productId}/add-stock")
    public ResponseEntity<ShopInventoryResponse> addStock(
            @PathVariable Long shopId,
            @PathVariable Long productId,
            @RequestBody StockUpdateRequest request) {

        try {
            ShopInventory inventory = shopInventoryService.addStock(shopId, productId, request.getQuantity());
            return ResponseEntity.ok(shopInventoryService.toResponse(inventory));
        } catch (IllegalArgumentException e) {
            log.error("Validation error adding stock: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (RuntimeException e) {
            log.error("Error adding stock: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get warehouse inventory for a product
     */
    @GetMapping("/warehouse/product/{productId}")
    public ResponseEntity<ShopInventoryResponse> getWarehouseInventory(@PathVariable Long productId) {
        Optional<ShopInventory> warehouseInventory = shopInventoryService.getWarehouseInventory(productId);
        return warehouseInventory.map(inv -> ResponseEntity.ok(shopInventoryService.toResponse(inv)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Delete inventory record
     */
    @DeleteMapping("/shop/{shopId}/product/{productId}")
    public ResponseEntity<Void> deleteInventory(
            @PathVariable Long shopId,
            @PathVariable Long productId) {

        try {
            shopInventoryService.deleteInventory(shopId, productId);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            log.error("Error deleting inventory", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Check if product is in stock with sufficient quantity
     */
    @GetMapping("/shop/{shopId}/product/{productId}/in-stock")
    public ResponseEntity<InStockResponse> checkInStock(
            @PathVariable Long shopId,
            @PathVariable Long productId,
            @RequestParam Integer quantity) {

        boolean inStock = shopInventoryService.isInStock(shopId, productId, quantity);
        InStockResponse response = new InStockResponse(inStock, quantity);
        return ResponseEntity.ok(response);
    }

    /**
     * Reduce stock (for sales or transfers)
     */
    @PostMapping("/shop/{shopId}/product/{productId}/reduce-stock")
    public ResponseEntity<ShopInventoryResponse> reduceStock(
            @PathVariable Long shopId,
            @PathVariable Long productId,
            @RequestBody StockUpdateRequest request) {

        try {
            ShopInventory inventory = shopInventoryService.reduceStock(shopId, productId, request.getQuantity());
            return ResponseEntity.ok(shopInventoryService.toResponse(inventory));
        } catch (RuntimeException e) {
            log.error("Error reducing stock", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get all products available in a specific shop
     */
    @GetMapping("/shop/{shopId}/products")
    public ResponseEntity<List<Product>> getProductsByShopId(@PathVariable Long shopId) {
        Optional<Shop> shop = shopRepository.findById(shopId);

        if (shop.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        List<Product> products = shopInventoryService.getProductsByShopId(shopId);
        return ResponseEntity.ok(products);
    }

    /**
     * Create new shop inventory with full details (WORLD-CLASS IMPLEMENTATION)
     *
     * Features:
     * - Strictly creates new records only (returns 400 if exists)
     * - Initializes totalStock with initial quantity for audit trail
     * - Validates maxStock limits before creation
     * - Requires all essential fields (supplier, currency, unitPrice)
     * - Use addStock() endpoint to increase stock after creation
     * - Use PATCH endpoint to update metadata (prices, thresholds, etc.)
     */
    @PostMapping
    public ResponseEntity<ShopInventoryResponse> createShopInventory(
            @Valid @RequestBody CreateShopInventoryRequest request) {

        try {
            ShopInventory inventory = shopInventoryService.createShopInventory(request);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(shopInventoryService.toResponse(inventory));
        } catch (IllegalArgumentException e) {
            log.error("Validation error creating shop inventory: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (RuntimeException e) {
            log.error("Error creating shop inventory: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update existing shop inventory with partial updates
     */
    @PatchMapping("/shop/{shopId}/product/{productId}")
    public ResponseEntity<ShopInventoryResponse> updateShopInventory(
            @PathVariable Long shopId,
            @PathVariable Long productId,
            @Valid @RequestBody UpdateShopInventoryRequest request) {

        try {
            ShopInventory inventory = shopInventoryService.updateShopInventory(shopId, productId, request);
            return ResponseEntity.ok(shopInventoryService.toResponse(inventory));
        } catch (RuntimeException e) {
            log.error("Error updating shop inventory", e);
            return ResponseEntity.badRequest().build();
        }
    }

}