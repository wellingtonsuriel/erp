package com.pos_onlineshop.hybrid.controllers;



import com.pos_onlineshop.hybrid.dtos.*;
import com.pos_onlineshop.hybrid.products.Product;
import com.pos_onlineshop.hybrid.products.ProductRepository;
import com.pos_onlineshop.hybrid.services.ShopInventoryService;
import com.pos_onlineshop.hybrid.shop.Shop;
import com.pos_onlineshop.hybrid.shop.ShopRepository;
import com.pos_onlineshop.hybrid.shopInventory.ShopInventory;
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
    public ResponseEntity<ShopInventory> getInventory(
            @PathVariable Long shopId,
            @PathVariable Long productId) {

        Optional<Shop> shop = shopRepository.findById(shopId);
        Optional<Product> product = productRepository.findById(productId);

        if (shop.isEmpty() || product.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Optional<ShopInventory> inventory = shopInventoryService.getInventory(shop.get(), product.get());
        return inventory.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get all inventory items for a shop
     */
    @GetMapping("/shop/{shopId}")
    public ResponseEntity<List<ShopInventory>> getShopInventory(@PathVariable Long shopId) {
        Optional<Shop> shop = shopRepository.findById(shopId);

        if (shop.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        List<ShopInventory> inventories = shopInventoryService.getShopInventory(shop.get());
        return ResponseEntity.ok(inventories);
    }

    /**
     * Get all inventory items for a product across all shops
     */
    @GetMapping("/product/{productId}")
    public ResponseEntity<List<ShopInventory>> getProductInventory(@PathVariable Long productId) {
        Optional<Product> product = productRepository.findById(productId);

        if (product.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        List<ShopInventory> inventories = shopInventoryService.getProductInventory(product.get());
        return ResponseEntity.ok(inventories);
    }

    /**
     * Create or update inventory
     */
    @PostMapping("/shop/{shopId}/product/{productId}")
    public ResponseEntity<ShopInventory> createOrUpdateInventory(
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
            return ResponseEntity.ok(inventory);
        } catch (Exception e) {
            log.error("Error creating/updating inventory", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Add stock to existing inventory
     */
    @PostMapping("/shop/{shopId}/product/{productId}/add-stock")
    public ResponseEntity<ShopInventory> addStock(
            @PathVariable Long shopId,
            @PathVariable Long productId,
            @RequestBody StockUpdateRequest request) {

        try {
            ShopInventory inventory = shopInventoryService.addStock(shopId, productId, request.getQuantity());
            return ResponseEntity.ok(inventory);
        } catch (RuntimeException e) {
            log.error("Error adding stock", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Reduce stock (for sales or transfers)
     */
    @PostMapping("/shop/{shopId}/product/{productId}/reduce-stock")
    public ResponseEntity<ShopInventory> reduceStock(
            @PathVariable Long shopId,
            @PathVariable Long productId,
            @RequestBody StockUpdateRequest request) {

        try {
            ShopInventory inventory = shopInventoryService.reduceStock(shopId, productId, request.getQuantity());
            return ResponseEntity.ok(inventory);
        } catch (RuntimeException e) {
            log.error("Error reducing stock", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get warehouse inventory for a product
     */
    @GetMapping("/warehouse/product/{productId}")
    public ResponseEntity<ShopInventory> getWarehouseInventory(@PathVariable Long productId) {
        Optional<ShopInventory> warehouseInventory = shopInventoryService.getWarehouseInventory(productId);
        return warehouseInventory.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Check if product is available in sufficient quantity
     */
    @GetMapping("/shop/{shopId}/product/{productId}/availability")
    public ResponseEntity<AvailabilityResponse> checkProductAvailability(
            @PathVariable Long shopId,
            @PathVariable Long productId,
            @RequestParam Integer requiredQuantity) {

        boolean available = shopInventoryService.isProductAvailable(shopId, productId, requiredQuantity);
        AvailabilityResponse response = new AvailabilityResponse(available, requiredQuantity);
        return ResponseEntity.ok(response);
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
    public ResponseEntity<ShopInventory> removeStock(
            @PathVariable Long shopId,
            @PathVariable Long productId,
            @RequestBody StockUpdateRequest request) {

        try {
            ShopInventory inventory = shopInventoryService.removeStock(shopId, productId, request.getQuantity());
            return ResponseEntity.ok(inventory);
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

}