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
}
