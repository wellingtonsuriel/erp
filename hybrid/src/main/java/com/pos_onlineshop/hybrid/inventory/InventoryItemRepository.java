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



    @Query(value = "SELECT SUM(CAST(i.quantity AS decimal) * p.price) " +
            "FROM inventory_items i " +
            "JOIN products p ON i.product_id = p.id", nativeQuery = true)
    BigDecimal calculateTotalInventoryValue();
}