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
     * Find prices within a price range
     */
    @Query("SELECT sp FROM SellingPrice sp WHERE sp.sellingPrice BETWEEN :minPrice AND :maxPrice " +
            "AND sp.active = true")
    List<SellingPrice> findByPriceRange(@Param("minPrice") BigDecimal minPrice,
                                        @Param("maxPrice") BigDecimal maxPrice);

    /**
     * Find promotional prices that are expiring soon
     */
    @Query("SELECT sp FROM SellingPrice sp WHERE sp.priceType = 'PROMOTIONAL' " +
            "AND sp.effectiveTo BETWEEN :now AND :expiryDate AND sp.active = true")
    List<SellingPrice> findExpiringPromotionalPrices(@Param("now") LocalDateTime now,
                                                     @Param("expiryDate") LocalDateTime expiryDate);

    /**
     * Find products without prices in a specific shop
     */
    @Query("SELECT p FROM Product p WHERE p NOT IN " +
            "(SELECT DISTINCT sp.product FROM SellingPrice sp WHERE sp.shop = :shop AND sp.active = true)")
    List<Product> findProductsWithoutPricesInShop(@Param("shop") Shop shop);

    /**
     * Count active prices by shop
     */
    @Query("SELECT COUNT(sp) FROM SellingPrice sp WHERE sp.shop = :shop AND sp.active = true")
    long countActiveByShop(@Param("shop") Shop shop);

    /**
     * Find prices that need review (low markup)
     */
    @Query("SELECT sp FROM SellingPrice sp WHERE sp.markupPercentage < :threshold AND sp.active = true")
    List<SellingPrice> findLowMarkupPrices(@Param("threshold") BigDecimal threshold);

    /**
     * Find duplicate prices (same product, shop, type, but different records)
     */
    @Query("SELECT sp FROM SellingPrice sp WHERE EXISTS " +
            "(SELECT sp2 FROM SellingPrice sp2 WHERE sp2.product = sp.product " +
            "AND sp2.shop = sp.shop AND sp2.priceType = sp.priceType " +
            "AND sp2.id != sp.id AND sp2.active = true) AND sp.active = true")
    List<SellingPrice> findDuplicatePrices();

    /**
     * Find prices by product ID and shop ID
     */
    List<SellingPrice> findByProduct_IdAndShop_IdAndActiveTrue(Long productId, Long shopId);

    /**
     * Find prices that are effective in a date range
     */
    @Query("SELECT sp FROM SellingPrice sp WHERE sp.active = true " +
            "AND (sp.effectiveFrom IS NULL OR sp.effectiveFrom <= :endDate) " +
            "AND (sp.effectiveTo IS NULL OR sp.effectiveTo >= :startDate)")
    List<SellingPrice> findEffectiveInDateRange(@Param("startDate") LocalDateTime startDate,
                                                @Param("endDate") LocalDateTime endDate);
}