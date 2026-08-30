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

    /**
     * Every lot for a (shop, product) pair, oldest first, locked for write - the FIFO
     * consumption/backfill order. Includes lots with remainingQuantity == null (not yet
     * backfilled) and == 0 (fully consumed) so callers can distinguish and handle both; see
     * InventoryValuationService.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT si FROM ShopInventory si WHERE si.shop.id = :shopId AND si.product.id = :productId ORDER BY si.id ASC")
    List<ShopInventory> findAllByShopIdAndProductIdOrderByIdAscWithLock(
            @Param("shopId") Long shopId, @Param("productId") Long productId);

    /**
     * Read-only variant of the above, for valuation reads that don't need to mutate layers
     * (e.g. a report) and shouldn't hold a write lock.
     */
    @Query("SELECT si FROM ShopInventory si WHERE si.shop.id = :shopId AND si.product.id = :productId ORDER BY si.id ASC")
    List<ShopInventory> findAllByShopIdAndProductIdOrderByIdAsc(
            @Param("shopId") Long shopId, @Param("productId") Long productId);

    /** Every distinct (shop, product) pair that has at least one lot - the driving set for a
     * full inventory valuation sweep. */
    @Query("SELECT DISTINCT si.shop.id, si.product.id FROM ShopInventory si")
    List<Object[]> findDistinctShopProductPairs();

    /**
     * Finds a previously system-restored layer by the reference that created it - the
     * idempotent-replay lookup for InventoryValuationService.restoreCostLayer: if a lot with
     * this exact (shop, product, sourceReference) already exists, that restoration already
     * happened and a retry must return it rather than inserting a second layer.
     */
    Optional<ShopInventory> findFirstByShopIdAndProductIdAndSourceReference(
            Long shopId, Long productId, String sourceReference);
}