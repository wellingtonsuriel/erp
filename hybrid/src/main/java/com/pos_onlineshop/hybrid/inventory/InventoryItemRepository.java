package com.pos_onlineshop.hybrid.inventory;


import com.pos_onlineshop.hybrid.products.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryItemRepository extends JpaRepository<InventoryItem, Long> {

    Optional<InventoryItem> findByProduct(Product product);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM InventoryItem i WHERE i.product.id = :productId")
    Optional<InventoryItem> findByProductIdWithLock(Long productId);

    List<InventoryItem> findByQuantityLessThanEqual(Integer quantity);

    @Query("SELECT i FROM InventoryItem i WHERE i.quantity <= i.reorderLevel")
    List<InventoryItem> findItemsNeedingReorder();

    @Query("SELECT SUM(i.quantity * i.product.price) FROM InventoryItem i")
    BigDecimal calculateTotalInventoryValue();
}