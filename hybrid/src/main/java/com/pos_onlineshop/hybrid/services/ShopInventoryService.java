package com.pos_onlineshop.hybrid.services;


import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.currency.CurrencyRepository;
import com.pos_onlineshop.hybrid.dtos.CreateShopInventoryRequest;
import com.pos_onlineshop.hybrid.dtos.ShopInventoryResponse;
import com.pos_onlineshop.hybrid.dtos.UpdateShopInventoryRequest;
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
     * Create inventory for a shop (CREATE ONLY - throws exception if already exists)
     * For world-class inventory management:
     * - Strictly creates new records only
     * - Initializes totalStock with initial quantity
     * - Validates maxStock limits if provided
     * - Requires all essential fields (supplier, currency, unitPrice)
     * - Use updateShopInventory() for metadata updates
     * - Use addStock() for stock increases
     */
    public ShopInventory createOrUpdateInventory(Shop shop, Product product, Integer quantity,
                                                 Integer inTransitQuantity, Long supplierId,
                                                 Long currencyId, BigDecimal unitPrice,
                                                 LocalDateTime expiryDate, Integer reorderLevel,
                                                 Integer minStock, Integer maxStock) {
        // Check if inventory already exists
        Optional<ShopInventory> existingInventory = shopInventoryRepository.findByShopAndProduct(shop, product);

        if (existingInventory.isPresent()) {
            throw new RuntimeException("Inventory already exists for shop " + shop.getCode() +
                    " and product " + product.getName() +
                    ". Use updateShopInventory() to modify metadata or addStock() to increase stock.");
        }

        // Validate required fields
        if (supplierId == null) {
            throw new IllegalArgumentException("Supplier is required when creating inventory");
        }
        if (currencyId == null) {
            throw new IllegalArgumentException("Currency is required when creating inventory");
        }
        if (unitPrice == null) {
            throw new IllegalArgumentException("Unit price is required when creating inventory");
        }

        // Resolve supplier and currency
        Suppliers supplier = suppliersRepository.findById(supplierId)
                .orElseThrow(() -> new RuntimeException("Supplier not found with id: " + supplierId));

        Currency currency = currencyRepository.findById(currencyId)
                .orElseThrow(() -> new RuntimeException("Currency not found with id: " + currencyId));

        // Initialize quantities with defaults
        int initialQuantity = quantity != null ? quantity : 0;
        int initialInTransit = inTransitQuantity != null ? inTransitQuantity : 0;

        // Validate against maxStock if provided
        if (maxStock != null && (initialQuantity + initialInTransit) > maxStock) {
            throw new IllegalArgumentException("Initial stock (" + (initialQuantity + initialInTransit) +
                    ") exceeds maximum stock limit (" + maxStock + ")");
        }

        // Create new inventory with totalStock initialized to initial quantity
        ShopInventory newInventory = ShopInventory.builder()
                .shop(shop)
                .product(product)
                .quantity(initialQuantity)
                .inTransitQuantity(initialInTransit)
                .totalStock(initialQuantity) // Initialize totalStock with initial quantity
                .suppliers(supplier)
                .currency(currency)
                .unitPrice(unitPrice)
                .expiryDate(expiryDate)
                .reorderLevel(reorderLevel)
                .minStock(minStock)
                .maxStock(maxStock)
                .build();

        ShopInventory savedInventory = shopInventoryRepository.save(newInventory);

        log.info("Created new inventory for shop {} and product {}: quantity = {}, totalStock = {}",
                shop.getCode(), product.getName(), initialQuantity, initialQuantity);

        return savedInventory;
    }

    /**
     * Add stock to existing inventory (WORLD-CLASS IMPLEMENTATION)
     * Features:
     * - Thread-safe with pessimistic locking
     * - Validates maxStock limits before adding
     * - Increments both quantity and totalStock
     * - Maintains audit trail via totalStock
     * - Atomic transaction
     */
    public ShopInventory addStock(Long shopId, Long productId, Integer additionalQuantity) {
        // Validate input
        if (additionalQuantity == null || additionalQuantity <= 0) {
            throw new IllegalArgumentException("Additional quantity must be positive, received: " + additionalQuantity);
        }

        // Use pessimistic lock for thread-safety
        Optional<ShopInventory> inventoryOpt = shopInventoryRepository.findByShopIdAndProductIdWithLock(shopId, productId);

        if (inventoryOpt.isEmpty()) {
            throw new RuntimeException("Inventory not found for shop " + shopId + " and product " + productId +
                    ". Create inventory first using createShopInventory()");
        }

        ShopInventory inventory = inventoryOpt.get();

        // Calculate new quantities
        int newQuantity = inventory.getQuantity() + additionalQuantity;
        int newTotalStock = inventory.getTotalStock() + additionalQuantity;

        // Validate against maxStock limit
        if (inventory.getMaxStock() != null && newQuantity > inventory.getMaxStock()) {
            throw new IllegalArgumentException(String.format(
                    "Cannot add stock: Would exceed maximum stock limit. " +
                    "Current: %d, Adding: %d, New Total: %d, Max Allowed: %d",
                    inventory.getQuantity(), additionalQuantity, newQuantity, inventory.getMaxStock()
            ));
        }

        // Update both current quantity and lifetime total
        inventory.setQuantity(newQuantity);
        inventory.setTotalStock(newTotalStock);

        ShopInventory savedInventory = shopInventoryRepository.save(inventory);

        log.info("Added {} items to inventory for shop {} and product {}: quantity {} -> {}, totalStock {} -> {}",
                additionalQuantity,
                inventory.getShop().getCode(),
                inventory.getProduct().getName(),
                inventory.getQuantity() - additionalQuantity,
                newQuantity,
                inventory.getTotalStock() - additionalQuantity,
                newTotalStock);

        return savedInventory;
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
     * Update in-transit quantity for inventory transfers
     * @param shopId the shop ID
     * @param productId the product ID
     * @param quantityChange the quantity change (positive to add, negative to subtract)
     */
    public ShopInventory updateInTransitQuantity(Long shopId, Long productId, Integer quantityChange) {
        Optional<ShopInventory> inventoryOpt = shopInventoryRepository.findByShopIdAndProductIdWithLock(shopId, productId);

        if (inventoryOpt.isEmpty()) {
            throw new RuntimeException("Inventory not found for shop " + shopId + " and product " + productId);
        }

        ShopInventory inventory = inventoryOpt.get();
        int newInTransitQuantity = inventory.getInTransitQuantity() + quantityChange;

        if (newInTransitQuantity < 0) {
            throw new RuntimeException("In-transit quantity cannot be negative. Current: " +
                    inventory.getInTransitQuantity() + ", Attempting to change by: " + quantityChange);
        }

        inventory.setInTransitQuantity(newInTransitQuantity);

        log.info("Updated in-transit quantity for shop {} and product {}: {} -> {}",
                inventory.getShop().getCode(),
                inventory.getProduct().getName(),
                inventory.getInTransitQuantity() - quantityChange,
                newInTransitQuantity);

        return shopInventoryRepository.save(inventory);
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

    /**
     * Get all products available in a specific shop
     */
    @Transactional(readOnly = true)
    public List<Product> getProductsByShopId(Long shopId) {
        return shopInventoryRepository.findProductsByShopId(shopId);
    }

    /**
     * Create new shop inventory with full details (WORLD-CLASS IMPLEMENTATION)
     * Features:
     * - Validates inventory doesn't already exist
     * - Initializes totalStock with initial quantity
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

        // Check if inventory already exists
        Optional<ShopInventory> existingInventory = shopInventoryRepository.findByShopAndProduct(shop, product);
        if (existingInventory.isPresent()) {
            throw new RuntimeException("Inventory already exists for shop " + shop.getCode() +
                    " and product " + product.getName() +
                    ". Use addStock() to increase stock or updateShopInventory() to modify metadata.");
        }

        // Initialize quantities
        int initialQuantity = request.getQuantity() != null ? request.getQuantity() : 0;
        int initialInTransit = request.getInTransitQuantity() != null ? request.getInTransitQuantity() : 0;

        // Validate against maxStock if provided
        if (request.getMaxStock() != null && (initialQuantity + initialInTransit) > request.getMaxStock()) {
            throw new IllegalArgumentException("Initial stock (" + (initialQuantity + initialInTransit) +
                    ") exceeds maximum stock limit (" + request.getMaxStock() + ")");
        }

        ShopInventory shopInventory = ShopInventory.builder()
                .shop(shop)
                .product(product)
                .suppliers(supplier)
                .currency(currency)
                .quantity(initialQuantity)
                .inTransitQuantity(initialInTransit)
                .totalStock(initialQuantity) // Initialize totalStock with initial quantity
                .unitPrice(request.getUnitPrice())
                .expiryDate(request.getExpiryDate())
                .reorderLevel(request.getReorderLevel())
                .minStock(request.getMinStock())
                .maxStock(request.getMaxStock())
                .build();

        ShopInventory savedInventory = shopInventoryRepository.save(shopInventory);
        log.info("Created shop inventory for shop {} and product {}: quantity = {}, totalStock = {}",
                shop.getCode(), product.getName(), initialQuantity, initialQuantity);

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

        if (request.getQuantity() != null) {
            inventory.setQuantity(request.getQuantity());
        }

        if (request.getInTransitQuantity() != null) {
            inventory.setInTransitQuantity(request.getInTransitQuantity());
        }

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
                .inTransitQuantity(inventory.getInTransitQuantity())
                .totalStock(inventory.getTotalStock())
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