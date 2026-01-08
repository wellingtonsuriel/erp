package com.pos_onlineshop.hybrid.services;


import com.pos_onlineshop.hybrid.products.Product;
import com.pos_onlineshop.hybrid.products.ProductRepository;
import com.pos_onlineshop.hybrid.shop.Shop;
import com.pos_onlineshop.hybrid.shop.ShopRepository;
import com.pos_onlineshop.hybrid.shopInventory.ShopInventory;
import com.pos_onlineshop.hybrid.shopInventory.ShopInventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ShopInventoryService {

    private final ShopInventoryRepository shopInventoryRepository;
    private final ShopRepository shopRepository;
    private final ProductRepository productRepository; // Add this dependency

    /**
     * Get inventory for a specific shop and product
     */
    @Transactional(readOnly = true)
    public Optional<ShopInventory> getInventory(Shop shop, Product product) {
        return shopInventoryRepository.findByShopAndProduct(shop, product);
    }

    /**
     * Get inventory with pessimistic lock for concurrent updates
     */
    @Transactional
    public Optional<ShopInventory> getInventoryWithLock(Long shopId, Long productId) {
        return shopInventoryRepository.findByShopIdAndProductIdWithLock(shopId, productId);
    }

    /**
     * Get all inventory items for a shop
     */
    @Transactional(readOnly = true)
    public List<ShopInventory> getShopInventory(Shop shop) {
        return shopInventoryRepository.findByShop(shop);
    }

    /**
     * Get all inventory items for a product across all shops
     */
    @Transactional(readOnly = true)
    public List<ShopInventory> getProductInventory(Product product) {
        return shopInventoryRepository.findByProduct(product);
    }

    /**
     * Create or update inventory for a shop
     */
    public ShopInventory createOrUpdateInventory(Shop shop, Product product, Integer quantity) {
        Optional<ShopInventory> existingInventory = shopInventoryRepository.findByShopAndProduct(shop, product);

        if (existingInventory.isPresent()) {
            ShopInventory inventory = existingInventory.get();
            inventory.setQuantity(quantity);
            log.info("Updated inventory for shop {} and product {}: new quantity = {}",
                    shop.getCode(), product.getName(), quantity);
            return shopInventoryRepository.save(inventory);
        } else {
            ShopInventory newInventory = ShopInventory.builder()
                    .shop(shop)
                    .product(product)
                    .quantity(quantity)
                    .build();
            log.info("Created new inventory for shop {} and product {}: quantity = {}",
                    shop.getCode(), product.getName(), quantity);
            return shopInventoryRepository.save(newInventory);
        }
    }

    /**
     * Add stock to existing inventory
     */
    public ShopInventory addStock(Long shopId, Long productId, Integer additionalQuantity) {
        Optional<ShopInventory> inventoryOpt = shopInventoryRepository.findByShopIdAndProductIdWithLock(shopId, productId);

        if (inventoryOpt.isEmpty()) {
            throw new RuntimeException("Inventory not found for shop " + shopId + " and product " + productId);
        }

        ShopInventory inventory = inventoryOpt.get();

        inventory.setQuantity(inventory.getQuantity() + additionalQuantity);

        log.info("Added {} items to inventory for shop {} and product {}",
                additionalQuantity, inventory.getShop().getCode(), inventory.getProduct().getName());

        return shopInventoryRepository.save(inventory);
    }

    /**
     * Remove stock (for sales or transfers) - alias for reduceStock
     */
    public ShopInventory removeStock(Long shopId, Long productId, Integer quantity) {
        return reduceStock(shopId, productId, quantity);
    }

    /**
     * Reduce stock (for sales or transfers)
     */
    public ShopInventory reduceStock(Long shopId, Long productId, Integer quantity) {
        Optional<ShopInventory> inventoryOpt = shopInventoryRepository.findByShopIdAndProductIdWithLock(shopId, productId);

        if (inventoryOpt.isEmpty()) {
            throw new RuntimeException("Inventory not found for shop " + shopId + " and product " + productId);
        }

        ShopInventory inventory = inventoryOpt.get();

        if (inventory.getQuantity() < quantity) {
            throw new RuntimeException("Insufficient stock. Available: " + inventory.getQuantity() +
                    ", Requested: " + quantity);
        }

        inventory.setQuantity(inventory.getQuantity() - quantity);

        log.info("Reduced {} items from inventory for shop {} and product {}",
                quantity, inventory.getShop().getCode(), inventory.getProduct().getName());

        return shopInventoryRepository.save(inventory);
    }


    /**
     * Get warehouse inventory for a product
     */
    @Transactional(readOnly = true)
    public Optional<ShopInventory> getWarehouseInventory(Long productId) {
        return shopInventoryRepository.findWarehouseInventory(productId);
    }

    /**
     * Check if product is in stock with sufficient quantity
     */
    @Transactional(readOnly = true)
    public boolean isInStock(Long shopId, Long productId, Integer requiredQuantity) {
        Optional<ShopInventory> inventoryOpt = shopInventoryRepository.findByShopAndProduct(
                shopRepository.findById(shopId).orElse(null),
                // Assuming you have ProductRepository injected
                productRepository.findById(productId).orElse(null)
        );

        if (inventoryOpt.isEmpty()) {
            return false;
        }

        ShopInventory inventory = inventoryOpt.get();
        return inventory.getQuantity() >= requiredQuantity;
    }

    /**
     * Check if product is available in sufficient quantity (alternative method)
     */
    @Transactional(readOnly = true)
    public boolean isProductAvailable(Long shopId, Long productId, Integer requiredQuantity) {
        return isInStock(shopId, productId, requiredQuantity);
    }



    /**
     * Delete inventory record
     */
    public void deleteInventory(Long shopId, Long productId) {
        Optional<ShopInventory> inventoryOpt = shopInventoryRepository.findByShopIdAndProductIdWithLock(shopId, productId);

        if (inventoryOpt.isEmpty()) {
            throw new RuntimeException("Inventory not found for shop " + shopId + " and product " + productId);
        }

        ShopInventory inventory = inventoryOpt.get();

        if (inventory.getQuantity() > 0) {
            throw new RuntimeException("Cannot delete inventory with existing stock");
        }

        shopInventoryRepository.delete(inventory);
        log.info("Deleted inventory for shop {} and product {}",
                inventory.getShop().getCode(), inventory.getProduct().getName());
    }
}