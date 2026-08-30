package com.pos_onlineshop.hybrid.inventoryMovement;

import com.pos_onlineshop.hybrid.enums.InventoryMovementType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, Long> {

    @Query("SELECT m FROM InventoryMovement m WHERE m.shop.id = :shopId AND m.product.id = :productId ORDER BY m.id ASC")
    List<InventoryMovement> findByShopIdAndProductIdOrderByIdAsc(
            @Param("shopId") Long shopId, @Param("productId") Long productId);

    @Query("SELECT m FROM InventoryMovement m WHERE m.shop.id = :shopId ORDER BY m.id DESC")
    List<InventoryMovement> findByShopIdOrderByIdDesc(@Param("shopId") Long shopId);

    @Query("SELECT m FROM InventoryMovement m WHERE m.product.id = :productId ORDER BY m.id DESC")
    List<InventoryMovement> findByProductIdOrderByIdDesc(@Param("productId") Long productId);

    List<InventoryMovement> findAllByOrderByIdDesc();

    /**
     * The idempotency lookup for InventoryValuationService's mutating operations
     * (consumeCostLayers/restoreCostLayer): (shop, product, movementType, reference) is the
     * natural key of one financially-significant inventory event (see each call site's
     * reference - always scoped to a single order line / transfer item / return line, never
     * shared across several). If a movement already exists for that key, the mutation it
     * represents already happened and must not be repeated on a retry.
     */
    Optional<InventoryMovement> findFirstByShopIdAndProductIdAndMovementTypeAndReference(
            Long shopId, Long productId, InventoryMovementType movementType, String reference);
}
