package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.enums.PriceType;
import com.pos_onlineshop.hybrid.productPrice.ProductPrice;
import com.pos_onlineshop.hybrid.productPrice.ProductPriceRepository;
import com.pos_onlineshop.hybrid.products.Product;
import com.pos_onlineshop.hybrid.products.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductPriceRepository productPriceRepository;
    private final InventoryService inventoryService;
    private final CurrencyService currencyService;

    /**
     * Create a new product
     * Note: Product entity does not have sku, barcode, price, baseCurrency fields.
     * These should be managed through ShopInventory and SellingPrice entities.
     */
    public Product createProduct(Product product) {
        log.info("Creating new product: {}", product.getName());

        // Validate required fields
        if (product.getName() == null || product.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Product name is required");
        }

        if (product.getCategory() == null || product.getCategory().trim().isEmpty()) {
            throw new IllegalArgumentException("Product category is required");
        }

        Product savedProduct = productRepository.save(product);

        log.info("Successfully created product: {} with ID: {}", savedProduct.getName(), savedProduct.getId());
        return savedProduct;
    }

    /**
     * Set or update product price for specific currency
     * Note: Product entity does not have price fields. Use SellingPriceService instead.
     * This method maintains ProductPrice for backward compatibility.
     */
    public void setProductPrice(Product product, Currency currency, BigDecimal price) {
        setProductPrice(product, currency, price, PriceType.REGULAR);
    }

    /**
     * Set or update product price with specific price type
     * Note: Product entity does not have price fields. Use SellingPriceService instead.
     * This method maintains ProductPrice for backward compatibility.
     */
    public void setProductPrice(Product product, Currency currency, BigDecimal price, PriceType priceType) {
        if (price == null || price.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Price must be non-negative");
        }

        Optional<ProductPrice> existingPrice = productPriceRepository.findByProductAndCurrency(product, currency);

        if (existingPrice.isPresent()) {
            // Update existing price
            ProductPrice productPrice = existingPrice.get();
            productPrice.setPrice(price);
            productPrice.setPriceType(priceType);
            productPrice.setActive(true);
            productPrice.setEffectiveDate(LocalDateTime.now());
            productPriceRepository.save(productPrice);
        } else {
            // Create new price entry
            createProductPrice(product, currency, price, priceType);
        }

        log.info("Set {} price for product {} in {}: {}",
                priceType, product.getName(), currency.getCode(), price);
    }

    /**
     * Set promotional/special price with time period
     * Note: Use SellingPriceService for shop-specific pricing.
     */
    public void setPromotionalPrice(Product product, Currency currency, BigDecimal promotionalPrice,
                                    LocalDateTime from, LocalDateTime to) {

        if (promotionalPrice == null || promotionalPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Promotional price must be non-negative");
        }

        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("Effective date cannot be after expiry date");
        }

        ProductPrice productPrice = ProductPrice.builder()
                .product(product)
                .currency(currency)
                .price(promotionalPrice)
                .priceType(PriceType.PROMOTIONAL)
                .effectiveDate(from != null ? from : LocalDateTime.now())
                .expiryDate(to)
                .active(true)
                .build();

        productPriceRepository.save(productPrice);

        log.info("Set promotional price for product {} in {}: {} (from {} to {})",
                product.getName(), currency.getCode(), promotionalPrice, from, to);
    }

    /**
     * Get effective product price in specific currency
     * Note: This uses ProductPrice table. For shop-specific prices, use SellingPriceService.
     */
    public BigDecimal getProductPrice(Product product, Currency currency) {
        Optional<ProductPrice> productPrice = productPriceRepository.findByProductAndCurrency(product, currency);

        if (productPrice.isPresent() && productPrice.get().isActive()) {
            return productPrice.get().getPrice();
        }

        return null;
    }

    /**
     * Get all active prices for a product
     */
    @Transactional(readOnly = true)
    public List<ProductPrice> getProductPrices(Long productId) {
        return productPriceRepository.findActiveByProductId(productId);
    }

    /**
     * Get product price with tax included
     * Note: Product entity does not have taxRate field.
     */
    public BigDecimal getProductPriceWithTax(Product product, Currency currency) {
        BigDecimal price = getProductPrice(product, currency);
        // Since Product doesn't have taxRate, return price as-is
        return price;
    }

    // CRUD Operations

    @Transactional(readOnly = true)
    public Optional<Product> findById(Long id) {
        return productRepository.findById(id);
    }

    /**
     * Find product by barcode
     * Note: Product entity does not have barcode field. Use ShopInventory to search by barcode.
     */
    @Transactional(readOnly = true)
    public Optional<Product> findByBarcode(String barcode) {
        // Product doesn't have barcode - this should query ShopInventory instead
        log.warn("Product entity does not have barcode field. Use ShopInventory to find by barcode.");
        return Optional.empty();
    }

    /**
     * Find product by SKU
     * Note: Product entity does not have sku field. Use ShopInventory to search by SKU.
     */
    @Transactional(readOnly = true)
    public Optional<Product> findBySku(String sku) {
        // Product doesn't have SKU - this should query ShopInventory instead
        log.warn("Product entity does not have sku field. Use ShopInventory to find by SKU.");
        return Optional.empty();
    }

    @Transactional(readOnly = true)
    public List<Product> findAll() {
        return productRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Page<Product> findAll(Pageable pageable) {
        return productRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public List<Product> findActiveProducts() {
        return productRepository.findByActiveTrue();
    }

    @Transactional(readOnly = true)
    public List<Product> findByCategory(String category) {
        return productRepository.findByCategory(category);
    }

    @Transactional(readOnly = true)
    public List<Product> searchByName(String name) {
        return productRepository.findByNameContainingIgnoreCase(name);
    }

    /**
     * Find products by price range
     * Note: Product entity does not have price field. Use SellingPrice or ProductPrice for price queries.
     */
    @Transactional(readOnly = true)
    public List<Product> findByPriceRange(BigDecimal minPrice, BigDecimal maxPrice) {
        log.warn("Product entity does not have price field. Use SellingPrice or ProductPrice for price queries.");
        return List.of();
    }

    @Transactional(readOnly = true)
    public List<Product> findWeighableProducts() {
        return productRepository.findByWeighableTrue();
    }

    @Transactional(readOnly = true)
    public List<String> getAllCategories() {
        return productRepository.findAllCategories();
    }

    /**
     * Update product details
     */
    public Product updateProduct(Long id, Product productDetails) {
        return productRepository.findById(id)
                .map(product -> {
                    // Update basic fields
                    if (productDetails.getName() != null) {
                        product.setName(productDetails.getName());
                    }
                    if (productDetails.getDescription() != null) {
                        product.setDescription(productDetails.getDescription());
                    }
                    if (productDetails.getCategory() != null) {
                        product.setCategory(productDetails.getCategory());
                    }
                    if (productDetails.getImageUrl() != null) {
                        product.setImageUrl(productDetails.getImageUrl());
                    }
                    if (productDetails.getWeight() != null) {
                        product.setWeight(productDetails.getWeight());
                    }
                    if (productDetails.getUnitOfMeasure() != null) {
                        product.setUnitOfMeasure(productDetails.getUnitOfMeasure());
                    }
                    if (productDetails.getActualMeasure() != null) {
                        product.setActualMeasure(productDetails.getActualMeasure());
                    }
                    if (productDetails.getMaxStock() != null) {
                        product.setMaxStock(productDetails.getMaxStock());
                    }
                    if (productDetails.getMinStock() != null) {
                        product.setMinStock(productDetails.getMinStock());
                    }

                    // Update weighable flag
                    product.setWeighable(productDetails.isWeighable());
                    product.setActive(productDetails.isActive());

                    Product updated = productRepository.save(product);

                    log.info("Updated product: {}", updated.getName());
                    return updated;
                })
                .orElseThrow(() -> new RuntimeException("Product not found: " + id));
    }

    /**
     * Soft delete product (set inactive)
     */
    public void deactivateProduct(Long id) {
        productRepository.findById(id)
                .ifPresentOrElse(
                        product -> {
                            product.setActive(false);
                            productRepository.save(product);
                            log.info("Deactivated product: {}", product.getName());
                        },
                        () -> {
                            throw new RuntimeException("Product not found: " + id);
                        }
                );
    }

    /**
     * Hard delete product
     */
    public void deleteProduct(Long id) {
        Optional<Product> productOpt = productRepository.findById(id);
        if (productOpt.isPresent()) {
            Product product = productOpt.get();

            // Delete associated prices first
            productPriceRepository.deleteByProduct(product);

            // Delete the product
            productRepository.deleteById(id);

            log.info("Deleted product: {} with ID: {}", product.getName(), id);
        } else {
            throw new RuntimeException("Product not found: " + id);
        }
    }

    /**
     * Activate/reactivate product
     */
    public void activateProduct(Long id) {
        productRepository.findById(id)
                .ifPresentOrElse(
                        product -> {
                            product.setActive(true);
                            productRepository.save(product);
                            log.info("Activated product: {}", product.getName());
                        },
                        () -> {
                            throw new RuntimeException("Product not found: " + id);
                        }
                );
    }

    /**
     * Remove price for specific currency
     */
    public void removeProductPrice(Long productId, Currency currency) {
        Product product = findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found: " + productId));

        productPriceRepository.findByProductAndCurrency(product, currency)
                .ifPresent(productPriceRepository::delete);

        log.info("Removed price for product {} in currency {}", product.getName(), currency.getCode());
    }

    /**
     * Helper method to create ProductPrice
     */
    private void createProductPrice(Product product, Currency currency, BigDecimal price, PriceType priceType) {
        ProductPrice productPrice = ProductPrice.builder()
                .product(product)
                .currency(currency)
                .price(price)
                .priceType(priceType)
                .effectiveDate(LocalDateTime.now())
                .active(true)
                .build();

        productPriceRepository.save(productPrice);
    }

    /**
     * Bulk update product prices by percentage
     */
    @Transactional
    public void updatePricesByPercentage(List<Long> productIds, BigDecimal percentage, Currency currency) {
        for (Long productId : productIds) {
            findById(productId).ifPresent(product -> {
                BigDecimal currentPrice = getProductPrice(product, currency);
                if (currentPrice != null) {
                    BigDecimal newPrice = currentPrice.multiply(BigDecimal.ONE.add(percentage.divide(BigDecimal.valueOf(100))));
                    setProductPrice(product, currency, newPrice);
                }
            });
        }
        log.info("Updated prices for {} products by {}% in currency {}", productIds.size(), percentage, currency.getCode());
    }
}