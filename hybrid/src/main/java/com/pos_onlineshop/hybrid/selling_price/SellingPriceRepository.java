package com.pos_onlineshop.hybrid.selling_price;



import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.enums.PriceType;
import com.pos_onlineshop.hybrid.products.Product;
import com.pos_onlineshop.hybrid.shop.Shop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SellingPriceRepository extends JpaRepository<SellingPrice, Long> {

    /**
     * Find active prices for a product in a specific shop
     */
    @Query("SELECT sp FROM SellingPrice sp WHERE sp.product = :product AND sp.shop = :shop " +
            "AND sp.active = true AND (sp.effectiveFrom IS NULL OR sp.effectiveFrom <= :now) " +
            "AND (sp.effectiveTo IS NULL OR sp.effectiveTo >= :now) " +
            "ORDER BY sp.priority DESC, sp.priceType")
    List<SellingPrice> findActiveByProductAndShop(@Param("product") Product product,
                                                  @Param("shop") Shop shop,
                                                  @Param("now") LocalDateTime now);

    /**
     * Find the best price for a product in a shop (highest priority)
     */
    @Query("SELECT sp FROM SellingPrice sp WHERE sp.product = :product AND sp.shop = :shop " +
            "AND sp.active = true AND (sp.effectiveFrom IS NULL OR sp.effectiveFrom <= :now) " +
            "AND (sp.effectiveTo IS NULL OR sp.effectiveTo >= :now) " +
            "ORDER BY sp.priority DESC, sp.priceType LIMIT 1")
    Optional<SellingPrice> findBestPriceByProductAndShop(@Param("product") Product product,
                                                         @Param("shop") Shop shop,
                                                         @Param("now") LocalDateTime now);

    /**
     * Find prices by product and shop with specific price type
     */
    List<SellingPrice> findByProductAndShopAndPriceTypeAndActiveTrue(Product product, Shop shop, PriceType priceType);

    /**
     * Find all active prices for a product across all shops
     */
    @Query("SELECT sp FROM SellingPrice sp WHERE sp.product = :product AND sp.active = true " +
            "AND (sp.effectiveFrom IS NULL OR sp.effectiveFrom <= :now) " +
            "AND (sp.effectiveTo IS NULL OR sp.effectiveTo >= :now)")
    List<SellingPrice> findActiveByProduct(@Param("product") Product product, @Param("now") LocalDateTime now);

    /**
     * Find all active prices for a shop
     */
    @Query("SELECT sp FROM SellingPrice sp WHERE sp.shop = :shop AND sp.active = true " +
            "AND (sp.effectiveFrom IS NULL OR sp.effectiveFrom <= :now) " +
            "AND (sp.effectiveTo IS NULL OR sp.effectiveTo >= :now)")
    List<SellingPrice> findActiveByShop(@Param("shop") Shop shop, @Param("now") LocalDateTime now);

    /**
     * Find prices by currency
     */
    List<SellingPrice> findByCurrencyAndActiveTrue(Currency currency);


    /**
     * Count active prices by shop
     */
    @Query("SELECT COUNT(sp) FROM SellingPrice sp WHERE sp.shop = :shop AND sp.active = true")
    long countActiveByShop(@Param("shop") Shop shop);

    /**
     * Find products without prices in a specific shop
     */
    @Query("SELECT p FROM Product p WHERE p NOT IN " +
            "(SELECT DISTINCT sp.product FROM SellingPrice sp WHERE sp.shop = :shop AND sp.active = true)")
    List<Product> findProductsWithoutPricesInShop(@Param("shop") Shop shop);



    @Query("SELECT sp FROM SellingPrice sp WHERE sp.active = true AND sp.effectiveTo IS NOT NULL " +
            "AND sp.effectiveTo BETWEEN :start AND :end")
    List<SellingPrice> findExpiringPromotionalPrices(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT sp FROM SellingPrice sp WHERE sp.sellingPrice BETWEEN :min AND :max AND sp.active = true")
    List<SellingPrice> findByPriceRange(@Param("min") BigDecimal min, @Param("max") BigDecimal max);



    // Adding the @Query annotation to fix the validation error.
    // This query assumes you want to find prices where the markup value is less than the provided threshold.
    // Adjust 'markup' to match the actual field name in your SellingPrice entity.
    @Query("SELECT sp FROM SellingPrice sp WHERE sp.active = true GROUP BY sp.product, sp.shop, sp.priceType HAVING COUNT(sp) > 1")
    List<SellingPrice> findDuplicatePrices();

    // Add other missing methods used in SellingPriceService if they aren't derived
    List<SellingPrice> findByProduct_IdAndShop_IdAndActiveTrue(Long productId, Long shopId);

    /**
     * Find prices with a low selling price value.
     * Note: Ensure 'sellingPrice' matches the field name in your SellingPrice entity.
     */
    @Query("SELECT s FROM SellingPrice s WHERE s.sellingPrice < :threshold")
    List<SellingPrice> findLowMarkupPrices(@Param("threshold") BigDecimal threshold);

}