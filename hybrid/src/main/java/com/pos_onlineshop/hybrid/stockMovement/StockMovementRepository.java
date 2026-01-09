package com.pos_onlineshop.hybrid.stockMovement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    /**
     * Find all stock movements for a specific shop and product
     */
    @Query("SELECT sm FROM StockMovement sm WHERE sm.shop.id = :shopId AND sm.product.id = :productId ORDER BY sm.createdAt DESC")
    List<StockMovement> findByShopIdAndProductId(@Param("shopId") Long shopId, @Param("productId") Long productId);

    /**
     * Find stock movements for a shop within a date range
     */
    @Query("SELECT sm FROM StockMovement sm WHERE sm.shop.id = :shopId AND sm.createdAt BETWEEN :startDate AND :endDate ORDER BY sm.createdAt DESC")
    List<StockMovement> findByShopIdAndDateRange(@Param("shopId") Long shopId,
                                                  @Param("startDate") LocalDateTime startDate,
                                                  @Param("endDate") LocalDateTime endDate);

    /**
     * Find stock movements by type for a shop and product
     */
    @Query("SELECT sm FROM StockMovement sm WHERE sm.shop.id = :shopId AND sm.product.id = :productId AND sm.movementType = :movementType ORDER BY sm.createdAt DESC")
    List<StockMovement> findByShopIdAndProductIdAndMovementType(@Param("shopId") Long shopId,
                                                                 @Param("productId") Long productId,
                                                                 @Param("movementType") StockMovement.MovementType movementType);
}
