package com.pos_onlineshop.hybrid.shopInventory;

import com.pos_onlineshop.hybrid.products.Product;
import com.pos_onlineshop.hybrid.shop.Shop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface ShopInventoryRepository extends JpaRepository<ShopInventory, Long> {

    Optional<ShopInventory> findByShopAndProduct(Shop shop, Product product);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT si FROM ShopInventory si WHERE si.shop.id = :shopId AND si.product.id = :productId")
    Optional<ShopInventory> findByShopIdAndProductIdWithLock(
            @Param("shopId") Long shopId,
            @Param("productId") Long productId);

    List<ShopInventory> findByShop(Shop shop);

    List<ShopInventory> findByProduct(Product product);



    @Query("SELECT si FROM ShopInventory si WHERE si.shop.type = 'WAREHOUSE' " +
            "AND si.product.id = :productId")
    Optional<ShopInventory> findWarehouseInventory(@Param("productId") Long productId);
}