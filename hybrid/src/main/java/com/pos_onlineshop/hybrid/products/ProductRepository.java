package com.pos_onlineshop.hybrid.products;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByCategory(String category);

    List<Product> findByNameContainingIgnoreCase(String name);

    List<Product> findByPriceBetween(BigDecimal minPrice, BigDecimal maxPrice);

    Optional<Product> findByBarcode(String barcode);

    Optional<Product> findBySku(String sku);






    /**
     * Find active products only
     */
    List<Product> findByActiveTrue();

    /**
     * Find weighable products
     */
    List<Product> findByWeighableTrue();

    /**
     * Find products by category (case insensitive)
     */
    @Query("SELECT p FROM Product p WHERE LOWER(p.category) = LOWER(:category) AND p.active = true")
    List<Product> findByCategoryIgnoreCase(@Param("category") String category);

    /**
     * Find all distinct categories
     */
    @Query("SELECT DISTINCT p.category FROM Product p WHERE p.active = true ORDER BY p.category")
    List<String> findAllCategories();

    /**
     * Search products by name or description
     */
    @Query("SELECT p FROM Product p WHERE (LOWER(p.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
            "OR LOWER(p.description) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) AND p.active = true")
    List<Product> searchByNameOrDescription(@Param("searchTerm") String searchTerm);




    /**
     * Find products with tax rate
     */
    List<Product> findByTaxRateGreaterThan(BigDecimal taxRate);



    /**
     * Count products by category
     */
    @Query("SELECT p.category, COUNT(p) FROM Product p WHERE p.active = true GROUP BY p.category")
    List<Object[]> countProductsByCategory();

    /**
     * Find products created in date range
     */
    @Query("SELECT p FROM Product p WHERE p.createdAt BETWEEN :startDate AND :endDate AND p.active = true")
    List<Product> findByCreatedAtBetween(@Param("startDate") java.time.LocalDateTime startDate,
                                         @Param("endDate") java.time.LocalDateTime endDate);

    /**
     * Find recently updated products
     */
    @Query("SELECT p FROM Product p WHERE p.updatedAt >= :since AND p.active = true ORDER BY p.updatedAt DESC")
    List<Product> findRecentlyUpdated(@Param("since") java.time.LocalDateTime since);

    /**
     * Find products without images
     */
    @Query("SELECT p FROM Product p WHERE (p.imageUrl IS NULL OR p.imageUrl = '') AND p.active = true")
    List<Product> findProductsWithoutImages();


}
