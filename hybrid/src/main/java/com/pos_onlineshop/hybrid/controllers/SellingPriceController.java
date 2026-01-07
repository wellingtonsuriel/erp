package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.currency.CurrencyRepository;
import com.pos_onlineshop.hybrid.dtos.*;
import com.pos_onlineshop.hybrid.enums.PriceType;
import com.pos_onlineshop.hybrid.products.Product;
import com.pos_onlineshop.hybrid.products.ProductRepository;
import com.pos_onlineshop.hybrid.selling_price.SellingPrice;
import com.pos_onlineshop.hybrid.services.SellingPriceService;
import com.pos_onlineshop.hybrid.shop.Shop;
import com.pos_onlineshop.hybrid.shop.ShopRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/selling-prices")
@RequiredArgsConstructor
@Slf4j
public class SellingPriceController {

    private final SellingPriceService sellingPriceService;
    private final ProductRepository productRepository;
    private final ShopRepository shopRepository;
    private final CurrencyRepository currencyRepository;

    /**
     * Create or update a selling price
     */
    @PostMapping
    public ResponseEntity<SellingPrice> createSellingPrice(@RequestBody SellingPriceCreateRequest request) {
        try {
            Optional<Product> product = productRepository.findById(request.getProductId());
            Optional<Shop> shop = shopRepository.findById(request.getShopId());
            Optional<Currency> currency = currencyRepository.findById(request.getCurrencyId());

            if (product.isEmpty() || shop.isEmpty() || currency.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            SellingPrice sellingPrice = SellingPrice.builder()
                    .product(product.get())
                    .shop(shop.get())
                    .currency(currency.get())
                    .priceType(request.getPriceType())
                    .sellingPrice(request.getSellingPrice())
                    .costPrice(request.getCostPrice())
                    .markupPercentage(request.getMarkupPercentage())
                    .discountPercentage(request.getDiscountPercentage())
                    .minSellingPrice(request.getMinSellingPrice())
                    .maxSellingPrice(request.getMaxSellingPrice())
                    .quantityBreak(request.getQuantityBreak())
                    .bulkPrice(request.getBulkPrice())
                    .effectiveFrom(request.getEffectiveFrom())
                    .effectiveTo(request.getEffectiveTo())
                    .priority(request.getPriority())
                    .createdBy(request.getCreatedBy())
                    .notes(request.getNotes())
                    .build();

            SellingPrice savedPrice = sellingPriceService.createOrUpdatePrice(sellingPrice);
            return ResponseEntity.status(HttpStatus.CREATED).body(savedPrice);
        } catch (IllegalArgumentException e) {
            log.error("Validation error creating selling price", e);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error creating selling price", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get current price for a product in a shop
     */
    @GetMapping("/shop/{shopId}/product/{productId}/current")
    public ResponseEntity<SellingPrice> getCurrentPrice(
            @PathVariable Long shopId,
            @PathVariable Long productId) {

        Optional<SellingPrice> price = sellingPriceService.getCurrentPrice(productId, shopId);
        return price.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get all active prices for a product in a shop
     */
    @GetMapping("/shop/{shopId}/product/{productId}")
    public ResponseEntity<List<SellingPrice>> getActivePrices(
            @PathVariable Long shopId,
            @PathVariable Long productId) {

        Optional<Product> product = productRepository.findById(productId);
        Optional<Shop> shop = shopRepository.findById(shopId);

        if (product.isEmpty() || shop.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        List<SellingPrice> prices = sellingPriceService.getActivePrices(product.get(), shop.get());
        return ResponseEntity.ok(prices);
    }

    /**
     * Get price by type
     */
    @GetMapping("/shop/{shopId}/product/{productId}/type/{priceType}")
    public ResponseEntity<SellingPrice> getPriceByType(
            @PathVariable Long shopId,
            @PathVariable Long productId,
            @PathVariable PriceType priceType) {

        Optional<Product> product = productRepository.findById(productId);
        Optional<Shop> shop = shopRepository.findById(shopId);

        if (product.isEmpty() || shop.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Optional<SellingPrice> price = sellingPriceService.getPriceByType(product.get(), shop.get(), priceType);
        return price.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get all prices for a product across all shops
     */
    @GetMapping("/product/{productId}")
    public ResponseEntity<List<SellingPrice>> getProductPrices(@PathVariable Long productId) {
        Optional<Product> product = productRepository.findById(productId);

        if (product.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        List<SellingPrice> prices = sellingPriceService.getProductPrices(product.get());
        return ResponseEntity.ok(prices);
    }

    /**
     * Get all prices for a shop
     */
    @GetMapping("/shop/{shopId}")
    public ResponseEntity<List<SellingPrice>> getShopPrices(@PathVariable Long shopId) {
        Optional<Shop> shop = shopRepository.findById(shopId);

        if (shop.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        List<SellingPrice> prices = sellingPriceService.getShopPrices(shop.get());
        return ResponseEntity.ok(prices);
    }

    /**
     * Set promotional price
     */
    @PostMapping("/shop/{shopId}/product/{productId}/promotional")
    public ResponseEntity<SellingPrice> setPromotionalPrice(
            @PathVariable Long shopId,
            @PathVariable Long productId,
            @RequestBody PromotionalPriceRequest request) {

        try {
            Optional<Product> product = productRepository.findById(productId);
            Optional<Shop> shop = shopRepository.findById(shopId);
            Optional<Currency> currency = currencyRepository.findById(request.getCurrencyId());

            if (product.isEmpty() || shop.isEmpty() || currency.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            SellingPrice promoPrice = sellingPriceService.setPromotionalPrice(
                    product.get(), shop.get(), currency.get(),
                    request.getPromotionalPrice(), request.getExpiryDate(),
                    request.getCreatedBy());

            return ResponseEntity.ok(promoPrice);
        } catch (Exception e) {
            log.error("Error setting promotional price", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Set bulk pricing
     */
    @PostMapping("/shop/{shopId}/product/{productId}/bulk")
    public ResponseEntity<SellingPrice> setBulkPrice(
            @PathVariable Long shopId,
            @PathVariable Long productId,
            @RequestBody BulkPriceRequest request) {

        try {
            Optional<Product> product = productRepository.findById(productId);
            Optional<Shop> shop = shopRepository.findById(shopId);
            Optional<Currency> currency = currencyRepository.findById(request.getCurrencyId());

            if (product.isEmpty() || shop.isEmpty() || currency.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            SellingPrice bulkPrice = sellingPriceService.setBulkPrice(
                    product.get(), shop.get(), currency.get(),
                    request.getRegularPrice(), request.getBulkPrice(),
                    request.getQuantityBreak(), request.getCreatedBy());

            return ResponseEntity.ok(bulkPrice);
        } catch (Exception e) {
            log.error("Error setting bulk price", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update price with cost calculation
     */
    @PutMapping("/{priceId}/cost-based")
    public ResponseEntity<SellingPrice> updatePriceWithCost(
            @PathVariable Long priceId,
            @RequestBody CostBasedPriceRequest request) {

        try {
            SellingPrice updatedPrice = sellingPriceService.updatePriceWithCost(
                    priceId, request.getCostPrice(), request.getMarkupPercentage());
            return ResponseEntity.ok(updatedPrice);
        } catch (RuntimeException e) {
            log.error("Error updating price with cost", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Calculate selling price from markup
     */
    @PostMapping("/calculate-from-markup")
    public ResponseEntity<PriceCalculationResponse> calculateSellingPrice(
            @RequestBody PriceCalculationRequest request) {

        try {
            BigDecimal sellingPrice = sellingPriceService.calculateSellingPriceFromMarkup(
                    request.getCostPrice(), request.getMarkupPercentage());

            PriceCalculationResponse response = new PriceCalculationResponse();
            response.setCostPrice(request.getCostPrice());
            response.setMarkupPercentage(request.getMarkupPercentage());
            response.setSellingPrice(sellingPrice);

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.error("Error calculating selling price", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Calculate markup percentage
     */
    @PostMapping("/calculate-markup")
    public ResponseEntity<MarkupCalculationResponse> calculateMarkup(
            @RequestBody MarkupCalculationRequest request) {

        try {
            BigDecimal markupPercentage = sellingPriceService.calculateMarkupPercentage(
                    request.getCostPrice(), request.getSellingPrice());

            MarkupCalculationResponse response = new MarkupCalculationResponse();
            response.setCostPrice(request.getCostPrice());
            response.setSellingPrice(request.getSellingPrice());
            response.setMarkupPercentage(markupPercentage);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error calculating markup", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Deactivate a price
     */
    @PostMapping("/{priceId}/deactivate")
    public ResponseEntity<Void> deactivatePrice(@PathVariable Long priceId) {
        try {
            sellingPriceService.deactivatePrice(priceId);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            log.error("Error deactivating price", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get prices in range
     */
    @GetMapping("/price-range")
    public ResponseEntity<List<SellingPrice>> getPricesInRange(
            @RequestParam BigDecimal minPrice,
            @RequestParam BigDecimal maxPrice) {

        List<SellingPrice> prices = sellingPriceService.getPricesInRange(minPrice, maxPrice);
        return ResponseEntity.ok(prices);
    }

    /**
     * Get products without prices in a shop
     */
    @GetMapping("/shop/{shopId}/products-without-prices")
    public ResponseEntity<List<Product>> getProductsWithoutPrices(@PathVariable Long shopId) {
        Optional<Shop> shop = shopRepository.findById(shopId);

        if (shop.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        List<Product> products = sellingPriceService.getProductsWithoutPrices(shop.get());
        return ResponseEntity.ok(products);
    }

    /**
     * Get low markup prices
     */
    @GetMapping("/low-markup")
    public ResponseEntity<List<SellingPrice>> getLowMarkupPrices(
            @RequestParam(defaultValue = "10.0") BigDecimal threshold) {

        List<SellingPrice> prices = sellingPriceService.getLowMarkupPrices(threshold);
        return ResponseEntity.ok(prices);
    }

    /**
     * Find duplicate prices
     */
    @GetMapping("/duplicates")
    public ResponseEntity<List<SellingPrice>> findDuplicatePrices() {
        List<SellingPrice> duplicates = sellingPriceService.findDuplicatePrices();
        return ResponseEntity.ok(duplicates);
    }

    /**
     * Bulk update prices
     */
    @PostMapping("/shop/{shopId}/bulk-update")
    public ResponseEntity<Void> bulkUpdatePrices(
            @PathVariable Long shopId,
            @RequestBody BulkUpdateRequest request) {

        try {
            Optional<Shop> shop = shopRepository.findById(shopId);
            if (shop.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            sellingPriceService.bulkUpdatePrices(
                    shop.get(), request.getPriceType(),
                    request.getPercentage(), request.getUpdatedBy());

            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Error bulk updating prices", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Copy prices from one shop to another
     */
    @PostMapping("/copy-prices")
    public ResponseEntity<Void> copyPricesFromShop(@RequestBody CopyPricesRequest request) {
        try {
            Optional<Shop> sourceShop = shopRepository.findById(request.getSourceShopId());
            Optional<Shop> targetShop = shopRepository.findById(request.getTargetShopId());

            if (sourceShop.isEmpty() || targetShop.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            sellingPriceService.copyPricesFromShop(
                    sourceShop.get(), targetShop.get(), request.getCreatedBy());

            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Error copying prices", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Expire promotional prices
     */
    @PostMapping("/expire-promotions")
    public ResponseEntity<Void> expirePromotionalPrices() {
        try {
            sellingPriceService.expirePromotionalPrices();
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Error expiring promotional prices", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Request/Response DTOs


}