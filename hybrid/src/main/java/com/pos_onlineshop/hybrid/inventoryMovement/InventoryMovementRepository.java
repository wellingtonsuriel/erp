package com.pos_onlineshop.hybrid.inventoryMovement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, Long> {

    @Query("SELECT m FROM InventoryMovement m WHERE m.shop.id = :shopId AND m.product.id = :productId ORDER BY m.id ASC")
    List<InventoryMovement> findByShopIdAndProductIdOrderByIdAsc(
            @Param("shopId") Long shopId, @Param("productId") Long productId);

    @Query("SELECT m FROM InventoryMovement m WHERE m.shop.id = :shopId ORDER BY m.id DESC")
    List<InventoryMovement> findByShopIdOrderByIdDesc(@Param("shopId") Long shopId);
}
