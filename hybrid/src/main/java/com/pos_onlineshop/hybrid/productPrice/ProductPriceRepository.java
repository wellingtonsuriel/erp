package com.pos_onlineshop.hybrid.productPrice;

import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.enums.PriceType;
import com.pos_onlineshop.hybrid.products.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductPriceRepository extends JpaRepository<ProductPrice, Long> {

    /**
     * Find price by product and currency
     */
    Optional<ProductPrice> findByProductAndCurrency(Product product, Currency currency);

    /**
     * Find active prices for a product
     */
    @Query("SELECT pp FROM ProductPrice pp WHERE pp.product.id = :productId AND pp.active = true " +
            "AND (pp.effectiveDate IS NULL OR pp.effectiveDate <= :now) " +
            "AND (pp.expiryDate IS NULL OR pp.expiryDate > :now) " +
            "ORDER BY pp.priceType, pp.effectiveDate DESC")
    List<ProductPrice> findActiveByProductId(@Param("productId") Long productId, @Param("now") LocalDateTime now);

    /**
     * Find active prices for a product (using current time)
     */
    default List<ProductPrice> findActiveByProductId(Long productId) {
        return findActiveByProductId(productId, LocalDateTime.now());
    }

    /**
     * Find all prices for a product
     */
    List<ProductPrice> findByProduct(Product product);

    /**
     * Find prices by product and currency (active only)
     */
    @Query("SELECT pp FROM ProductPrice pp WHERE pp.product = :product AND pp.currency = :currency " +
            "AND pp.active = true " +
            "AND (pp.effectiveDate IS NULL OR pp.effectiveDate <= :now) " +
            "AND (pp.expiryDate IS NULL OR pp.expiryDate > :now) " +
            "ORDER BY pp.priceType, pp.effectiveDate DESC")
    List<ProductPrice> findActiveByProductAndCurrency(@Param("product") Product product,
                                                      @Param("currency") Currency currency,
                                                      @Param("now") LocalDateTime now);

    /**
     * Find prices by currency
     */
    List<ProductPrice> findByCurrency(Currency currency);

    /**
     * Find prices by price type
     */
    List<ProductPrice> findByPriceType(PriceType priceType);

    /**
     * Find promotional prices
     */
    @Query("SELECT pp FROM ProductPrice pp WHERE pp.priceType IN ('PROMOTIONAL', 'SALE') " +
            "AND pp.active = true " +
            "AND (pp.effectiveDate IS NULL OR pp.effectiveDate <= :now) " +
            "AND (pp.expiryDate IS NULL OR pp.expiryDate > :now)")
    List<ProductPrice> findActivePromotionalPrices(@Param("now") LocalDateTime now);

    /**
     * Find expired prices
     */
    @Query("SELECT pp FROM ProductPrice pp WHERE pp.expiryDate IS NOT NULL AND pp.expiryDate <= :now")
    List<ProductPrice> findExpiredPrices(@Param("now") LocalDateTime now);

    /**
     * Find prices within date range
     */
    @Query("SELECT pp FROM ProductPrice pp WHERE pp.effectiveDate BETWEEN :startDate AND :endDate")
    List<ProductPrice> findByEffectiveDateBetween(@Param("startDate") LocalDateTime startDate,
                                                  @Param("endDate") LocalDateTime endDate);

    /**
     * Find prices by price range
     */
    @Query("SELECT pp FROM ProductPrice pp WHERE pp.price BETWEEN :minPrice AND :maxPrice")
    List<ProductPrice> findByPriceRange(@Param("minPrice") BigDecimal minPrice,
                                        @Param("maxPrice") BigDecimal maxPrice);

    /**
     * Count active prices for product
     */
    @Query("SELECT COUNT(pp) FROM ProductPrice pp WHERE pp.product.id = :productId AND pp.active = true " +
            "AND (pp.effectiveDate IS NULL OR pp.effectiveDate <= :now) " +
            "AND (pp.expiryDate IS NULL OR pp.expiryDate > :now)")
    Long countActiveByProductId(@Param("productId") Long productId, @Param("now") LocalDateTime now);

    /**
     * Delete all prices for a product
     */
    void deleteByProduct(Product product);

    /**
     * Delete prices by product and currency
     */
    void deleteByProductAndCurrency(Product product, Currency currency);

    /**
     * Find lowest price for product across all currencies (converted to base currency)
     */
    @Query("SELECT MIN(pp.price) FROM ProductPrice pp WHERE pp.product.id = :productId AND pp.active = true " +
            "AND (pp.effectiveDate IS NULL OR pp.effectiveDate <= :now) " +
            "AND (pp.expiryDate IS NULL OR pp.expiryDate > :now)")
    Optional<BigDecimal> findLowestPriceByProductId(@Param("productId") Long productId, @Param("now") LocalDateTime now);

    /**
     * Find highest price for product across all currencies
     */
    @Query("SELECT MAX(pp.price) FROM ProductPrice pp WHERE pp.product.id = :productId AND pp.active = true " +
            "AND (pp.effectiveDate IS NULL OR pp.effectiveDate <= :now) " +
            "AND (pp.expiryDate IS NULL OR pp.expiryDate > :now)")
    Optional<BigDecimal> findHighestPriceByProductId(@Param("productId") Long productId, @Param("now") LocalDateTime now);

    /**
     * Find products with prices in specific currency
     */
    @Query("SELECT DISTINCT pp.product FROM ProductPrice pp WHERE pp.currency = :currency AND pp.active = true")
    List<Product> findProductsByCurrency(@Param("currency") Currency currency);

    /**
     * Update all prices for a currency by percentage
     */
    @Query("UPDATE ProductPrice pp SET pp.price = pp.price * (1 + :percentage/100) " +
            "WHERE pp.currency = :currency AND pp.active = true")
    int updatePricesByCurrencyAndPercentage(@Param("currency") Currency currency,
                                            @Param("percentage") BigDecimal percentage);

    /**
     * Deactivate expired prices
     */
    @Query("UPDATE ProductPrice pp SET pp.active = false WHERE pp.expiryDate IS NOT NULL AND pp.expiryDate <= :now")
    int deactivateExpiredPrices(@Param("now") LocalDateTime now);

    /**
     * Find products with multiple active prices (promotions)
     */
    @Query("SELECT p FROM Product p WHERE " +
            "(SELECT COUNT(pp) FROM ProductPrice pp WHERE pp.product = p AND pp.active = true " +
            "AND (pp.effectiveDate IS NULL OR pp.effectiveDate <= :now) " +
            "AND (pp.expiryDate IS NULL OR pp.expiryDate > :now)) > 1")
    List<Product> findProductsWithMultiplePrices(@Param("now") LocalDateTime now);

    /**
     * Get price statistics for a product
     */
    @Query("SELECT MIN(pp.price), MAX(pp.price), AVG(pp.price), COUNT(pp) " +
            "FROM ProductPrice pp WHERE pp.product.id = :productId AND pp.active = true")
    Object[] getPriceStatisticsByProductId(@Param("productId") Long productId);
}