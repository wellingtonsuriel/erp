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
     * Find inventory total by shop and product with pessimistic lock
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT it FROM InventoryTotal it WHERE it.shop.id = :shopId AND it.product.id = :productId")
    Optional<InventoryTotal> findByShopIdAndProductIdWithLock(@Param("shopId") Long shopId, @Param("productId") Long productId);

    /**
     * Find all inventory totals for a shop
     */
    List<InventoryTotal> findByShop(Shop shop);

    /**
     * Find all inventory totals for a product
     */
    List<InventoryTotal> findByProduct(Product product);

    /**
     * Find inventory total by shop ID and product ID
     */
    @Query("SELECT it FROM InventoryTotal it WHERE it.shop.id = :shopId AND it.product.id = :productId")
    Optional<InventoryTotal> findByShopIdAndProductId(@Param("shopId") Long shopId, @Param("productId") Long productId);
}
