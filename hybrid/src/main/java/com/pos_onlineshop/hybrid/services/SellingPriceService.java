package com.pos_onlineshop.hybrid.services;



import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.dtos.SellingPriceResponse;
import com.pos_onlineshop.hybrid.dtos.SellingPriceSummaryResponse;
import com.pos_onlineshop.hybrid.dtos.TaxResponse;
import com.pos_onlineshop.hybrid.enums.PriceType;
import com.pos_onlineshop.hybrid.products.Product;

import com.pos_onlineshop.hybrid.selling_price.SellingPrice;
import com.pos_onlineshop.hybrid.selling_price.SellingPriceRepository;
import com.pos_onlineshop.hybrid.shop.Shop;
import com.pos_onlineshop.hybrid.tax.Tax;
import com.pos_onlineshop.hybrid.tax.TaxRepository;
import com.pos_onlineshop.hybrid.mappers.TaxMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class SellingPriceService {

    private final SellingPriceRepository sellingPriceRepository;
    private final TaxRepository taxRepository;
    private final TaxMapper taxMapper;

    /**
     * Create or update a selling price
     * Note: SellingPrice entity does not have costPrice and markupPercentage fields.
     */
    public SellingPrice createOrUpdatePrice(SellingPrice sellingPrice) {
        validateSellingPrice(sellingPrice);

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
     * Find selling price by ID
     */
    public SellingPrice findById(Long priceId) {
        return sellingPriceRepository.findById(priceId)
                .orElseThrow(() -> new RuntimeException("Selling price not found: " + priceId));
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
     * Note: SellingPrice entity does not have costPrice/markupPercentage fields.
     * This is a utility method for external use.
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
     * Note: SellingPrice entity does not have costPrice/markupPercentage fields.
     * This is a utility method for external use.
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
     * Note: SellingPrice entity does not have costPrice/markupPercentage fields.
     * This method only updates the selling price.
     */
    public SellingPrice updatePriceWithCost(Long priceId, BigDecimal costPrice, BigDecimal markupPercentage) {
        SellingPrice price = sellingPriceRepository.findById(priceId)
                .orElseThrow(() -> new RuntimeException("Selling price not found: " + priceId));

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
     * Update existing price with new data
     */
    public SellingPrice updatePrice(Long priceId, SellingPrice updates, String updatedBy) {
        SellingPrice existingPrice = sellingPriceRepository.findById(priceId)
                .orElseThrow(() -> new RuntimeException("Selling price not found: " + priceId));

        if (updates.getPriceType() != null) {
            existingPrice.setPriceType(updates.getPriceType());
        }
        if (updates.getSellingPrice() != null) {
            existingPrice.setSellingPrice(updates.getSellingPrice());
        }
        if (updates.getBasePrice() != null) {
            existingPrice.setBasePrice(updates.getBasePrice());
        }
        if (updates.getTaxes() != null) {
            existingPrice.setTaxes(updates.getTaxes());
        }
        if (updates.getDiscountPercentage() != null) {
            existingPrice.setDiscountPercentage(updates.getDiscountPercentage());
        }
        if (updates.getMinSellingPrice() != null) {
            existingPrice.setMinSellingPrice(updates.getMinSellingPrice());
        }
        if (updates.getMaxSellingPrice() != null) {
            existingPrice.setMaxSellingPrice(updates.getMaxSellingPrice());
        }
        if (updates.getQuantityBreak() != null) {
            existingPrice.setQuantityBreak(updates.getQuantityBreak());
        }
        if (updates.getBulkPrice() != null) {
            existingPrice.setBulkPrice(updates.getBulkPrice());
        }
        if (updates.getEffectiveFrom() != null) {
            existingPrice.setEffectiveFrom(updates.getEffectiveFrom());
        }
        if (updates.getEffectiveTo() != null) {
            existingPrice.setEffectiveTo(updates.getEffectiveTo());
        }
        if (updates.getPriority() != null) {
            existingPrice.setPriority(updates.getPriority());
        }
        if (updates.getNotes() != null) {
            existingPrice.setNotes(updates.getNotes());
        }

        existingPrice.setUpdatedBy(updatedBy);

        validateSellingPrice(existingPrice);

        return sellingPriceRepository.save(existingPrice);
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

    /**
     * Convert SellingPrice entity to SellingPriceResponse DTO
     */
    public SellingPriceResponse toResponse(SellingPrice sellingPrice) {
        List<TaxResponse> taxResponses = new ArrayList<>();
        if (sellingPrice.getTaxes() != null) {
            taxResponses = sellingPrice.getTaxes().stream()
                    .map(taxMapper::toResponse)
                    .collect(Collectors.toList());
        }

        return SellingPriceResponse.builder()
                .id(sellingPrice.getId())
                .productId(sellingPrice.getProduct().getId())
                .productName(sellingPrice.getProduct().getName())
                .shopId(sellingPrice.getShop().getId())
                .shopName(sellingPrice.getShop().getName())
                .currencyId(sellingPrice.getCurrency().getId())
                .currencyCode(sellingPrice.getCurrency().getCode())
                .priceType(sellingPrice.getPriceType())
                .sellingPrice(sellingPrice.getSellingPrice())
                .basePrice(sellingPrice.getBasePrice())
                .taxes(taxResponses)
                .discountPercentage(sellingPrice.getDiscountPercentage())
                .finalPrice(sellingPrice.getFinalPrice())
                .minSellingPrice(sellingPrice.getMinSellingPrice())
                .maxSellingPrice(sellingPrice.getMaxSellingPrice())
                .quantityBreak(sellingPrice.getQuantityBreak())
                .bulkPrice(sellingPrice.getBulkPrice())
                .effectiveFrom(sellingPrice.getEffectiveFrom())
                .effectiveTo(sellingPrice.getEffectiveTo())
                .active(sellingPrice.isActive())
                .currentlyEffective(sellingPrice.isCurrentlyEffective())
                .priority(sellingPrice.getPriority())
                .createdBy(sellingPrice.getCreatedBy())
                .updatedBy(sellingPrice.getUpdatedBy())
                .createdAt(sellingPrice.getCreatedAt())
                .updatedAt(sellingPrice.getUpdatedAt())
                .notes(sellingPrice.getNotes())
                .build();
    }

    /**
     * Convert list of SellingPrice entities to SellingPriceResponse DTOs
     */
    public List<SellingPriceResponse> toResponseList(List<SellingPrice> sellingPrices) {
        return sellingPrices.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Convert SellingPrice entity to SellingPriceSummaryResponse DTO
     */
    public SellingPriceSummaryResponse toSummaryResponse(SellingPrice sellingPrice) {
        return SellingPriceSummaryResponse.builder()
                .id(sellingPrice.getId())
                .productId(sellingPrice.getProduct().getId())
                .productName(sellingPrice.getProduct().getName())
                .priceType(sellingPrice.getPriceType())
                .sellingPrice(sellingPrice.getSellingPrice())
                .finalPrice(sellingPrice.getFinalPrice())
                .currentlyEffective(sellingPrice.isCurrentlyEffective())
                .effectiveFrom(sellingPrice.getEffectiveFrom())
                .effectiveTo(sellingPrice.getEffectiveTo())
                .priority(sellingPrice.getPriority())
                .build();
    }

    /**
     * Convert list of SellingPrice entities to SellingPriceSummaryResponse DTOs
     */
    public List<SellingPriceSummaryResponse> toSummaryResponseList(List<SellingPrice> sellingPrices) {
        return sellingPrices.stream()
                .map(this::toSummaryResponse)
                .collect(Collectors.toList());
    }
}