package com.pos_onlineshop.hybrid.inventoryAdjustment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InventoryAdjustmentRepository extends JpaRepository<InventoryAdjustment, Long> {

    Optional<InventoryAdjustment> findByReference(String reference);

    List<InventoryAdjustment> findAllByOrderByCreatedAtDesc();

    List<InventoryAdjustment> findAllByShopIdOrderByCreatedAtDesc(Long shopId);
}
