package com.pos_onlineshop.hybrid.services;



import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.enums.PriceType;
import com.pos_onlineshop.hybrid.products.Product;

import com.pos_onlineshop.hybrid.selling_price.SellingPrice;
import com.pos_onlineshop.hybrid.selling_price.SellingPriceRepository;
import com.pos_onlineshop.hybrid.shop.Shop;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class SellingPriceService {

    private final SellingPriceRepository sellingPriceRepository;

    /**
     * Create or update a selling price
     */
    public SellingPrice createOrUpdatePrice(SellingPrice sellingPrice) {
        validateSellingPrice(sellingPrice);

        // Auto-calculate markup if cost price is provided
        if (sellingPrice.getCostPrice() != null && sellingPrice.getSellingPrice() != null) {
            sellingPrice.setMarkupPercentage(calculateMarkupPercentage(
                    sellingPrice.getCostPrice(), sellingPrice.getSellingPrice()));
        }

        // Set default effective date if not provided
        if (sellingPrice.getEffectiveFrom() == null) {
            sellingPrice.setEffectiveFrom(LocalDateTime.now());
        }

        SellingPrice savedPrice = sellingPriceRepository.save(sellingPrice);
        log.info("Created/updated selling price for product {} in shop {}: {}",
                sellingPrice.getProduct().getId(),
                sellingPrice.getShop().getId(),
                sellingPrice.getSellingPrice());

        return savedPrice;
    }

    /**
     * Get the current effective price for a product in a shop
     */
    public Optional<SellingPrice> getCurrentPrice(Product product, Shop shop) {
        return sellingPriceRepository.findBestPriceByProductAndShop(product, shop, LocalDateTime.now());
    }

    /**
     * Get the current effective price by IDs
     */
    public Optional<SellingPrice> getCurrentPrice(Long productId, Long shopId) {
        List<SellingPrice> prices = sellingPriceRepository.findByProduct_IdAndShop_IdAndActiveTrue(productId, shopId);
        return prices.stream()
                .filter(SellingPrice::isCurrentlyEffective)
                .findFirst();
    }

    /**
     * Get all active prices for a product in a shop
     */
    public List<SellingPrice> getActivePrices(Product product, Shop shop) {
        return sellingPriceRepository.findActiveByProductAndShop(product, shop, LocalDateTime.now());
    }

    /**
     * Get price for specific type
     */
    public Optional<SellingPrice> getPriceByType(Product product, Shop shop, PriceType priceType) {
        List<SellingPrice> prices = sellingPriceRepository
                .findByProductAndShopAndPriceTypeAndActiveTrue(product, shop, priceType);

        return prices.stream()
                .filter(SellingPrice::isCurrentlyEffective)
                .findFirst();
    }

    /**
     * Get all active prices for a product across all shops
     */
    public List<SellingPrice> getProductPrices(Product product) {
        return sellingPriceRepository.findActiveByProduct(product, LocalDateTime.now());
    }

    /**
     * Get all active prices for a shop
     */
    public List<SellingPrice> getShopPrices(Shop shop) {
        return sellingPriceRepository.findActiveByShop(shop, LocalDateTime.now());
    }

    /**
     * Set promotional price with expiry
     */
    public SellingPrice setPromotionalPrice(Product product, Shop shop, Currency currency,
                                            BigDecimal promotionalPrice, LocalDateTime expiryDate,
                                            String createdBy) {

        SellingPrice promoPrice = SellingPrice.builder()
                .product(product)
                .shop(shop)
                .currency(currency)
                .priceType(PriceType.PROMOTIONAL)
                .sellingPrice(promotionalPrice)
                .effectiveFrom(LocalDateTime.now())
                .effectiveTo(expiryDate)
                .priority(100) // High priority for promotional prices
                .createdBy(createdBy)
                .build();

        return createOrUpdatePrice(promoPrice);
    }

    /**
     * Set bulk pricing
     */
    public SellingPrice setBulkPrice(Product product, Shop shop, Currency currency,
                                     BigDecimal regularPrice, BigDecimal bulkPrice,
                                     Integer quantityBreak, String createdBy) {

        SellingPrice bulkPricing = SellingPrice.builder()
                .product(product)
                .shop(shop)
                .currency(currency)
                .priceType(PriceType.BULK)
                .sellingPrice(regularPrice)
                .bulkPrice(bulkPrice)
                .quantityBreak(quantityBreak)
                .createdBy(createdBy)
                .build();

        return createOrUpdatePrice(bulkPricing);
    }

    /**
     * Calculate selling price from cost price and markup percentage
     */
    public BigDecimal calculateSellingPriceFromMarkup(BigDecimal costPrice, BigDecimal markupPercentage) {
        if (costPrice == null || markupPercentage == null) {
            throw new IllegalArgumentException("Cost price and markup percentage cannot be null");
        }

        BigDecimal markupAmount = costPrice.multiply(markupPercentage)
                .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);

        return costPrice.add(markupAmount);
    }

    /**
     * Calculate markup percentage from cost and selling price
     */
    public BigDecimal calculateMarkupPercentage(BigDecimal costPrice, BigDecimal sellingPrice) {
        if (costPrice == null || sellingPrice == null || costPrice.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal profit = sellingPrice.subtract(costPrice);
        return profit.multiply(BigDecimal.valueOf(100))
                .divide(costPrice, 2, RoundingMode.HALF_UP);
    }

    /**
     * Update price with cost-based calculation
     */
    public SellingPrice updatePriceWithCost(Long priceId, BigDecimal costPrice, BigDecimal markupPercentage) {
        SellingPrice price = sellingPriceRepository.findById(priceId)
                .orElseThrow(() -> new RuntimeException("Selling price not found: " + priceId));

        price.setCostPrice(costPrice);
        price.setMarkupPercentage(markupPercentage);
        price.setSellingPrice(calculateSellingPriceFromMarkup(costPrice, markupPercentage));

        return sellingPriceRepository.save(price);
    }

    /**
     * Deactivate a price
     */
    public void deactivatePrice(Long priceId) {
        SellingPrice price = sellingPriceRepository.findById(priceId)
                .orElseThrow(() -> new RuntimeException("Selling price not found: " + priceId));

        price.setActive(false);
        sellingPriceRepository.save(price);

        log.info("Deactivated selling price: {}", priceId);
    }

    /**
     * Expire promotional prices
     */
    @Transactional
    public void expirePromotionalPrices() {
        List<SellingPrice> expiringPrices = sellingPriceRepository
                .findExpiringPromotionalPrices(LocalDateTime.now(), LocalDateTime.now());

        for (SellingPrice price : expiringPrices) {
            price.setActive(false);
            sellingPriceRepository.save(price);
        }

        log.info("Expired {} promotional prices", expiringPrices.size());
    }

    /**
     * Get prices within a range
     */
    public List<SellingPrice> getPricesInRange(BigDecimal minPrice, BigDecimal maxPrice) {
        return sellingPriceRepository.findByPriceRange(minPrice, maxPrice);
    }

    /**
     * Get products without prices in a shop
     */
    public List<Product> getProductsWithoutPrices(Shop shop) {
        return sellingPriceRepository.findProductsWithoutPricesInShop(shop);
    }

    /**
     * Get low markup prices for review
     */
    public List<SellingPrice> getLowMarkupPrices(BigDecimal threshold) {
        return sellingPriceRepository.findLowMarkupPrices(threshold);
    }

    /**
     * Find and resolve duplicate prices
     */
    public List<SellingPrice> findDuplicatePrices() {
        return sellingPriceRepository.findDuplicatePrices();
    }

    /**
     * Bulk update prices by percentage
     */
    @Transactional
    public void bulkUpdatePrices(Shop shop, PriceType priceType, BigDecimal percentage, String updatedBy) {
        List<SellingPrice> prices = sellingPriceRepository.findActiveByShop(shop, LocalDateTime.now());

        for (SellingPrice price : prices) {
            if (priceType == null || price.getPriceType() == priceType) {
                BigDecimal increase = price.getSellingPrice().multiply(percentage)
                        .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);

                price.setSellingPrice(price.getSellingPrice().add(increase));
                price.setUpdatedBy(updatedBy);

                // Recalculate markup if cost price exists
                if (price.getCostPrice() != null) {
                    price.setMarkupPercentage(calculateMarkupPercentage(
                            price.getCostPrice(), price.getSellingPrice()));
                }
            }
        }

        sellingPriceRepository.saveAll(prices);
        log.info("Bulk updated {} prices in shop {} by {}%", prices.size(), shop.getName(), percentage);
    }

    /**
     * Copy prices from one shop to another
     */
    @Transactional
    public void copyPricesFromShop(Shop sourceShop, Shop targetShop, String createdBy) {
        List<SellingPrice> sourcePrices = sellingPriceRepository.findActiveByShop(sourceShop, LocalDateTime.now());

        for (SellingPrice sourcePrice : sourcePrices) {
            // Check if price already exists for this product in target shop
            Optional<SellingPrice> existingPrice = getCurrentPrice(sourcePrice.getProduct(), targetShop);

            if (existingPrice.isEmpty()) {
                SellingPrice newPrice = SellingPrice.builder()
                        .product(sourcePrice.getProduct())
                        .shop(targetShop)
                        .currency(sourcePrice.getCurrency())
                        .priceType(sourcePrice.getPriceType())
                        .sellingPrice(sourcePrice.getSellingPrice())
                        .costPrice(sourcePrice.getCostPrice())
                        .markupPercentage(sourcePrice.getMarkupPercentage())
                        .discountPercentage(sourcePrice.getDiscountPercentage())
                        .minSellingPrice(sourcePrice.getMinSellingPrice())
                        .maxSellingPrice(sourcePrice.getMaxSellingPrice())
                        .quantityBreak(sourcePrice.getQuantityBreak())
                        .bulkPrice(sourcePrice.getBulkPrice())
                        .priority(sourcePrice.getPriority())
                        .createdBy(createdBy)
                        .notes("Copied from " + sourceShop.getName())
                        .build();

                sellingPriceRepository.save(newPrice);
            }
        }

        log.info("Copied prices from shop {} to shop {}", sourceShop.getName(), targetShop.getName());
    }

    /**
     * Validate selling price data
     */
    private void validateSellingPrice(SellingPrice sellingPrice) {
        if (sellingPrice.getProduct() == null) {
            throw new IllegalArgumentException("Product cannot be null");
        }

        if (sellingPrice.getShop() == null) {
            throw new IllegalArgumentException("Shop cannot be null");
        }

        if (sellingPrice.getCurrency() == null) {
            throw new IllegalArgumentException("Currency cannot be null");
        }

        if (sellingPrice.getSellingPrice() == null || sellingPrice.getSellingPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Selling price must be greater than zero");
        }

        if (sellingPrice.getMinSellingPrice() != null &&
                sellingPrice.getSellingPrice().compareTo(sellingPrice.getMinSellingPrice()) < 0) {
            throw new IllegalArgumentException("Selling price cannot be less than minimum price");
        }

        if (sellingPrice.getMaxSellingPrice() != null &&
                sellingPrice.getSellingPrice().compareTo(sellingPrice.getMaxSellingPrice()) > 0) {
            throw new IllegalArgumentException("Selling price cannot be greater than maximum price");
        }

        if (sellingPrice.getEffectiveTo() != null && sellingPrice.getEffectiveFrom() != null &&
                sellingPrice.getEffectiveTo().isBefore(sellingPrice.getEffectiveFrom())) {
            throw new IllegalArgumentException("Effective to date cannot be before effective from date");
        }
    }
}