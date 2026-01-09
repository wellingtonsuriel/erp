package com.pos_onlineshop.hybrid.services;


import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.currency.CurrencyRepository;
import com.pos_onlineshop.hybrid.dtos.CreateShopInventoryRequest;
import com.pos_onlineshop.hybrid.dtos.ShopInventoryResponse;
import com.pos_onlineshop.hybrid.dtos.UpdateShopInventoryRequest;
import com.pos_onlineshop.hybrid.inventoryTotal.InventoryTotal;
import com.pos_onlineshop.hybrid.inventoryTotal.InventoryTotalRepository;
import com.pos_onlineshop.hybrid.products.Product;
import com.pos_onlineshop.hybrid.products.ProductRepository;
import com.pos_onlineshop.hybrid.shop.Shop;
import com.pos_onlineshop.hybrid.shop.ShopRepository;
import com.pos_onlineshop.hybrid.shopInventory.ShopInventory;
import com.pos_onlineshop.hybrid.shopInventory.ShopInventoryRepository;
import com.pos_onlineshop.hybrid.suppliers.Suppliers;
import com.pos_onlineshop.hybrid.suppliers.SuppliersRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ShopInventoryService {

    private final ShopInventoryRepository shopInventoryRepository;
    private final ShopRepository shopRepository;
    private final ProductRepository productRepository;
    private final SuppliersRepository suppliersRepository;
    private final CurrencyRepository currencyRepository;
    private final InventoryTotalRepository inventoryTotalRepository;

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
     * Add stock - creates audit trail record and updates inventory total
     */
    public InventoryTotal addStock(Long shopId, Long productId, Integer additionalQuantity) {
        return addStock(shopId, productId, additionalQuantity, null);
    }

    /**
     * Add stock with notes - creates audit trail record and updates inventory total
     */
    public InventoryTotal addStock(Long shopId, Long productId, Integer additionalQuantity, String notes) {
        // Validate input
        if (additionalQuantity == null || additionalQuantity <= 0) {
            throw new IllegalArgumentException("Additional quantity must be positive, received: " + additionalQuantity);
        }

        // Get shop and product
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new RuntimeException("Shop not found with id: " + shopId));
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + productId));

        // Update or create inventory total with pessimistic lock
        Optional<InventoryTotal> inventoryTotalOpt = inventoryTotalRepository.findByShopIdAndProductIdWithLock(shopId, productId);

        InventoryTotal inventoryTotal;
        if (inventoryTotalOpt.isPresent()) {
            inventoryTotal = inventoryTotalOpt.get();
            inventoryTotal.setTotalstock(inventoryTotal.getTotalstock() + additionalQuantity);
            inventoryTotal.setLastUpdated(LocalDateTime.now());
        } else {
            // Create new inventory total if it doesn't exist
            inventoryTotal = InventoryTotal.builder()
                    .shop(shop)
                    .product(product)
                    .totalstock(additionalQuantity)
                    .lastUpdated(LocalDateTime.now())
                    .build();
        }

        InventoryTotal savedTotal = inventoryTotalRepository.save(inventoryTotal);

        log.info("Added {} items to inventory for shop {} and product {}. New total stock: {}",
                additionalQuantity, shop.getCode(), product.getName(), savedTotal.getTotalstock());

        return savedTotal;
    }

    /**
     * Reduce stock - creates audit trail record and updates inventory total
     */
    public InventoryTotal reduceStock(Long shopId, Long productId, Integer quantity) {
        return reduceStock(shopId, productId, quantity, null);
    }

    /**
     * Reduce stock with notes - creates audit trail record and updates inventory total
     */
    public InventoryTotal reduceStock(Long shopId, Long productId, Integer quantity, String notes) {
        // Validate input
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive, received: " + quantity);
        }

        // Get shop and product
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new RuntimeException("Shop not found with id: " + shopId));
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + productId));

        // Get inventory total with pessimistic lock
        Optional<InventoryTotal> inventoryTotalOpt = inventoryTotalRepository.findByShopIdAndProductIdWithLock(shopId, productId);

        if (inventoryTotalOpt.isEmpty()) {
            throw new RuntimeException("Inventory total not found for shop " + shopId + " and product " + productId);
        }

        InventoryTotal inventoryTotal = inventoryTotalOpt.get();

        if (inventoryTotal.getTotalstock() < quantity) {
            throw new RuntimeException("Insufficient stock. Available: " + inventoryTotal.getTotalstock() +
                    ", Requested: " + quantity);
        }

        // Update inventory total
        inventoryTotal.setTotalstock(inventoryTotal.getTotalstock() - quantity);
        inventoryTotal.setLastUpdated(LocalDateTime.now());

        InventoryTotal savedTotal = inventoryTotalRepository.save(inventoryTotal);

        log.info("Reduced {} items from inventory for shop {} and product {}. New total stock: {}",
                quantity, shop.getCode(), product.getName(), savedTotal.getTotalstock());

        return savedTotal;
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
     * Checks against inventoryTotal (current available stock)
     */
    @Transactional(readOnly = true)
    public boolean isInStock(Long shopId, Long productId, Integer requiredQuantity) {
        Shop shop = shopRepository.findById(shopId).orElse(null);
        Product product = productRepository.findById(productId).orElse(null);

        if (shop == null || product == null) {
            return false;
        }

        Optional<InventoryTotal> inventoryTotalOpt = inventoryTotalRepository.findByShopAndProduct(shop, product);

        if (inventoryTotalOpt.isEmpty()) {
            return false;
        }

        InventoryTotal inventoryTotal = inventoryTotalOpt.get();
        return inventoryTotal.getTotalstock() >= requiredQuantity;
    }

    /**
     * Delete inventory record and inventory total
     */
    public void deleteInventory(Long shopId, Long productId) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new RuntimeException("Shop not found with id: " + shopId));
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + productId));

        // Check if there's any stock in inventory total
        Optional<InventoryTotal> inventoryTotalOpt = inventoryTotalRepository.findByShopIdAndProductIdWithLock(shopId, productId);

        if (inventoryTotalOpt.isPresent()) {
            InventoryTotal inventoryTotal = inventoryTotalOpt.get();
            if (inventoryTotal.getTotalstock() > 0) {
                throw new RuntimeException("Cannot delete inventory with existing stock. Current stock: " + inventoryTotal.getTotalstock());
            }
            inventoryTotalRepository.delete(inventoryTotal);
        }

        // Delete shop inventory if it exists
        Optional<ShopInventory> inventoryOpt = shopInventoryRepository.findByShopAndProduct(shop, product);
        if (inventoryOpt.isPresent()) {
            shopInventoryRepository.delete(inventoryOpt.get());
        }

        log.info("Deleted inventory for shop {} and product {}", shop.getCode(), product.getName());
    }

    /**
     * Get all products available in a specific shop
     */
    @Transactional(readOnly = true)
    public List<Product> getProductsByShopId(Long shopId) {
        return shopInventoryRepository.findProductsByShopId(shopId);
    }

    /**
     * Create new shop inventory with full details
     * Features:
     * - Creates a new record each time for audit/record-keeping purposes
     * - Adds quantity to InventoryTotal (cumulative stock tracking)
     * - Validates maxStock limits
     * - Strict validation of all required fields
     */
    public ShopInventory createShopInventory(CreateShopInventoryRequest request) {
        Shop shop = shopRepository.findById(request.getShopId())
                .orElseThrow(() -> new RuntimeException("Shop not found with id: " + request.getShopId()));

        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + request.getProductId()));

        Suppliers supplier = suppliersRepository.findById(request.getSupplierId())
                .orElseThrow(() -> new RuntimeException("Supplier not found with id: " + request.getSupplierId()));

        Currency currency = currencyRepository.findById(request.getCurrencyId())
                .orElseThrow(() -> new RuntimeException("Currency not found with id: " + request.getCurrencyId()));

        // Initialize quantities
        int initialQuantity = request.getQuantity() != null ? request.getQuantity() : 0;

        // Validate against maxStock if provided
        if (request.getMaxStock() != null && initialQuantity > request.getMaxStock()) {
            throw new IllegalArgumentException("Initial stock (" + initialQuantity +
                    ") exceeds maximum stock limit (" + request.getMaxStock() + ")");
        }

        // Always create a new inventory record (for audit trail)
        ShopInventory shopInventory = ShopInventory.builder()
                .shop(shop)
                .product(product)
                .suppliers(supplier)
                .currency(currency)
                .quantity(request.getQuantity())
                .unitPrice(request.getUnitPrice())
                .expiryDate(request.getExpiryDate())
                .reorderLevel(request.getReorderLevel())
                .minStock(request.getMinStock())
                .maxStock(request.getMaxStock())
                .build();

        ShopInventory savedInventory = shopInventoryRepository.save(shopInventory);

        // Add to inventory total (cumulative tracking)
        if (initialQuantity > 0) {
            addStock(shop.getId(), product.getId(), initialQuantity);
            log.info("Created shop inventory record for shop {} and product {}: quantity = {}",
                    shop.getCode(), product.getName(), initialQuantity);
        } else {
            log.info("Created shop inventory record for shop {} and product {} with zero quantity",
                    shop.getCode(), product.getName());
        }

        return savedInventory;
    }

    /**
     * Update existing shop inventory
     */
    public ShopInventory updateShopInventory(Long shopId, Long productId, UpdateShopInventoryRequest request) {
        Optional<ShopInventory> inventoryOpt = shopInventoryRepository.findByShopIdAndProductIdWithLock(shopId, productId);

        if (inventoryOpt.isEmpty()) {
            throw new RuntimeException("Inventory not found for shop " + shopId + " and product " + productId);
        }

        ShopInventory inventory = inventoryOpt.get();

        // Update fields if provided
        if (request.getSupplierId() != null) {
            Suppliers supplier = suppliersRepository.findById(request.getSupplierId())
                    .orElseThrow(() -> new RuntimeException("Supplier not found with id: " + request.getSupplierId()));
            inventory.setSuppliers(supplier);
        }

        if (request.getCurrencyId() != null) {
            Currency currency = currencyRepository.findById(request.getCurrencyId())
                    .orElseThrow(() -> new RuntimeException("Currency not found with id: " + request.getCurrencyId()));
            inventory.setCurrency(currency);
        }

        // Note: quantity is immutable (for audit trail) and cannot be updated
        // Use addStock() or reduceStock() methods to change totalStock

        if (request.getUnitPrice() != null) {
            inventory.setUnitPrice(request.getUnitPrice());
        }

        if (request.getExpiryDate() != null) {
            inventory.setExpiryDate(request.getExpiryDate());
        }

        if (request.getReorderLevel() != null) {
            inventory.setReorderLevel(request.getReorderLevel());
        }

        if (request.getMinStock() != null) {
            inventory.setMinStock(request.getMinStock());
        }

        if (request.getMaxStock() != null) {
            inventory.setMaxStock(request.getMaxStock());
        }

        ShopInventory updatedInventory = shopInventoryRepository.save(inventory);
        log.info("Updated shop inventory for shop {} and product {}",
                inventory.getShop().getCode(), inventory.getProduct().getName());

        return updatedInventory;
    }

    /**
     * Convert ShopInventory entity to ShopInventoryResponse DTO
     */
    public ShopInventoryResponse toResponse(ShopInventory inventory) {
        // Get total stock from inventory total
        Integer totalStock = 0;
        if (inventory.getShop() != null && inventory.getProduct() != null) {
            Optional<InventoryTotal> inventoryTotalOpt = inventoryTotalRepository.findByShopAndProduct(
                    inventory.getShop(), inventory.getProduct());
            if (inventoryTotalOpt.isPresent()) {
                totalStock = inventoryTotalOpt.get().getTotalstock();
            }
        }

        return ShopInventoryResponse.builder()
                .id(inventory.getId())
                .shopId(inventory.getShop() != null ? inventory.getShop().getId() : null)
                .shopCode(inventory.getShop() != null ? inventory.getShop().getCode() : null)
                .shopName(inventory.getShop() != null ? inventory.getShop().getName() : null)
                .productId(inventory.getProduct() != null ? inventory.getProduct().getId() : null)
                .productName(inventory.getProduct() != null ? inventory.getProduct().getName() : null)
                .productBarcode(inventory.getProduct() != null ? inventory.getProduct().getBarcode() : null)
                .supplierId(inventory.getSuppliers() != null ? inventory.getSuppliers().getId() : null)
                .supplierName(inventory.getSuppliers() != null ? inventory.getSuppliers().getName() : null)
                .quantity(inventory.getQuantity())
                .totalStock(totalStock)
                .currencyId(inventory.getCurrency() != null ? inventory.getCurrency().getId() : null)
                .currencyCode(inventory.getCurrency() != null ? inventory.getCurrency().getCode() : null)
                .unitPrice(inventory.getUnitPrice())
                .expiryDate(inventory.getExpiryDate())
                .reorderLevel(inventory.getReorderLevel())
                .minStock(inventory.getMinStock())
                .maxStock(inventory.getMaxStock())
                .addedAt(inventory.getAddedAt())
                .build();
    }

    /**
     * Convert list of ShopInventory entities to list of ShopInventoryResponse DTOs
     */
    @Transactional(readOnly = true)
    public List<ShopInventoryResponse> toResponseList(List<ShopInventory> inventories) {
        return inventories.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }
}