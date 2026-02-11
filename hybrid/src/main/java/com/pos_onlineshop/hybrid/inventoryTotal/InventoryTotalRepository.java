package com.pos_onlineshop.hybrid.inventoryTotal;

import com.pos_onlineshop.hybrid.products.Product;
import com.pos_onlineshop.hybrid.shop.Shop;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryTotalRepository extends JpaRepository<InventoryTotal, Long> {

    /**
     * Find inventory total by shop and product
     */
    Optional<InventoryTotal> findByShopAndProduct(Shop shop, Product product);

    /**
     * Find inventory total by shop ID and product ID with pessimistic lock
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT it FROM InventoryTotal it WHERE it.shop.id = :shopId AND it.product.id = :productId")
    Optional<InventoryTotal> findByShopIdAndProductIdWithLock(@Param("shopId") Long shopId, @Param("productId") Long productId);

    /**
     * Find all inventory totals for a specific shop
     */
    @Query("SELECT it FROM InventoryTotal it WHERE it.shop.id = :shopId")
    List<InventoryTotal> findByShopId(@Param("shopId") Long shopId);

    /**
     * Find all inventory totals for a specific product across all shops
     */
    @Query("SELECT it FROM InventoryTotal it WHERE it.product.id = :productId")
    List<InventoryTotal> findByProductId(@Param("productId") Long productId);

    /**
     * Get total stock across all shops for a product
     */
    @Query("SELECT COALESCE(SUM(it.totalstock), 0) FROM InventoryTotal it WHERE it.product.id = :productId")
    Integer getTotalStockForProduct(@Param("productId") Long productId);

    /**
     * Get total stock units across all shops
     */
    @Query("SELECT COALESCE(SUM(it.totalstock), 0) FROM InventoryTotal it")
    Integer sumTotalStockAllShops();

    /**
     * Count distinct products that have stock across all shops
     */
    @Query("SELECT COUNT(DISTINCT it.product.id) FROM InventoryTotal it WHERE it.totalstock > 0")
    Integer countProductsWithStock();

    /**
     * Find items that are out of stock (totalstock = 0)
     */
    @Query("SELECT it FROM InventoryTotal it WHERE it.totalstock = 0")
    List<InventoryTotal> findOutOfStockItems();

    /**
     * Get total stock units for a specific shop
     */
    @Query("SELECT COALESCE(SUM(it.totalstock), 0) FROM InventoryTotal it WHERE it.shop.id = :shopId")
    Integer sumTotalStockByShop(@Param("shopId") Long shopId);

    /**
     * Count distinct products with stock in a specific shop
     */
    @Query("SELECT COUNT(DISTINCT it.product.id) FROM InventoryTotal it WHERE it.shop.id = :shopId AND it.totalstock > 0")
    Integer countProductsWithStockByShop(@Param("shopId") Long shopId);

    /**
     * Find all inventory totals with fetched shop and product for reporting
     */
    @Query("SELECT it FROM InventoryTotal it JOIN FETCH it.shop JOIN FETCH it.product")
    List<InventoryTotal> findAllWithShopAndProduct();

    /**
     * Find all inventory totals for a shop with fetched product details
     */
    @Query("SELECT it FROM InventoryTotal it JOIN FETCH it.shop JOIN FETCH it.product WHERE it.shop.id = :shopId")
    List<InventoryTotal> findByShopIdWithDetails(@Param("shopId") Long shopId);

    /**
     * Find all inventory totals for a product across all shops with fetched shop and product details
     */
    @Query("SELECT it FROM InventoryTotal it JOIN FETCH it.shop JOIN FETCH it.product WHERE it.product.id = :productId")
    List<InventoryTotal> findByProductIdWithDetails(@Param("productId") Long productId);
}
