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
     * Create or update inventory (legacy - for backward compatibility)
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
                    shop.get(), product.get(), request.getQuantity());
            return ResponseEntity.ok(shopInventoryService.toResponse(inventory));
        } catch (Exception e) {
            log.error("Error creating/updating inventory", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Add stock to existing inventory
     */
    @PostMapping("/shop/{shopId}/product/{productId}/add-stock")
    public ResponseEntity<ShopInventoryResponse> addStock(
            @PathVariable Long shopId,
            @PathVariable Long productId,
            @RequestBody StockUpdateRequest request) {

        try {
            ShopInventory inventory = shopInventoryService.addStock(shopId, productId, request.getQuantity());
            return ResponseEntity.ok(shopInventoryService.toResponse(inventory));
        } catch (RuntimeException e) {
            log.error("Error adding stock", e);
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
     * Remove stock (alias for reduce stock)
     */
    @PostMapping("/shop/{shopId}/product/{productId}/remove-stock")
    public ResponseEntity<ShopInventoryResponse> removeStock(
            @PathVariable Long shopId,
            @PathVariable Long productId,
            @RequestBody StockUpdateRequest request) {

        try {
            ShopInventory inventory = shopInventoryService.removeStock(shopId, productId, request.getQuantity());
            return ResponseEntity.ok(shopInventoryService.toResponse(inventory));
        } catch (RuntimeException e) {
            log.error("Error removing stock", e);
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
     * Create new shop inventory with full details
     */
    @PostMapping
    public ResponseEntity<ShopInventoryResponse> createShopInventory(
            @Valid @RequestBody CreateShopInventoryRequest request) {

        try {
            ShopInventory inventory = shopInventoryService.createShopInventory(request);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(shopInventoryService.toResponse(inventory));
        } catch (RuntimeException e) {
            log.error("Error creating shop inventory", e);
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