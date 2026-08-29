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

    // Get the most recent shop inventory record for a shop-product combination
    // This handles cases where multiple records exist (for audit trail purposes)
    Optional<ShopInventory> findFirstByShopAndProductOrderByIdDesc(Shop shop, Product product);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT si FROM ShopInventory si WHERE si.shop.id = :shopId AND si.product.id = :productId ORDER BY si.id DESC")
    Optional<ShopInventory> findByShopIdAndProductIdWithLock(
            @Param("shopId") Long shopId,
            @Param("productId") Long productId);

    List<ShopInventory> findByShop(Shop shop);

    List<ShopInventory> findByProduct(Product product);



    @Query("SELECT si FROM ShopInventory si WHERE si.shop.type = 'WAREHOUSE' " +
            "AND si.product.id = :productId ORDER BY si.id DESC")
    Optional<ShopInventory> findWarehouseInventory(@Param("productId") Long productId);

    @Query("SELECT si.product FROM ShopInventory si WHERE si.shop.id = :shopId")
    List<Product> findProductsByShopId(@Param("shopId") Long shopId);

    /** One row per (shop, product) pair - the latest by id, same "latest row wins" convention
     * as findFirstByShopAndProductOrderByIdDesc, generalized across every pair at once. This
     * is the correct way to value total on-hand inventory: ShopInventory rows are not
     * decremented lots summed together, each new row supersedes the previous one for that
     * pair (see InventoryTransferService/ShopInventoryService for where new rows are created). */
    @Query("SELECT si FROM ShopInventory si WHERE si.id IN (" +
            "SELECT MAX(si2.id) FROM ShopInventory si2 GROUP BY si2.shop.id, si2.product.id)")
    List<ShopInventory> findLatestPerShopAndProduct();
}