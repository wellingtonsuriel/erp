package com.pos_onlineshop.hybrid.inventoryTransfer;

import com.pos_onlineshop.hybrid.enums.TransferPriority;
import com.pos_onlineshop.hybrid.enums.TransferStatus;
import com.pos_onlineshop.hybrid.shop.Shop;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryTransferRepository extends JpaRepository<InventoryTransfer, Long> {

    Optional<InventoryTransfer> findByTransferNumber(String transferNumber);

    Page<InventoryTransfer> findByFromShop(Shop shop, Pageable pageable);

    Page<InventoryTransfer> findByToShop(Shop shop, Pageable pageable);

    List<InventoryTransfer> findByStatus(TransferStatus status);

    List<InventoryTransfer> findByStatusAndPriority(TransferStatus status, TransferPriority priority);

    @Query("SELECT it FROM InventoryTransfer it WHERE (it.fromShop.id = :shopId OR it.toShop.id = :shopId) " +
            "ORDER BY it.requestedAt DESC")
    Page<InventoryTransfer> findByShopInvolved(@Param("shopId") Long shopId, Pageable pageable);

    @Query("SELECT it FROM InventoryTransfer it WHERE it.requestedAt BETWEEN :startDate AND :endDate")
    List<InventoryTransfer> findByDateRange(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    @Query("SELECT it FROM InventoryTransfer it WHERE it.status = 'IN_TRANSIT' " +
            "AND it.expectedDelivery < :now")
    List<InventoryTransfer> findOverdueTransfers(@Param("now") LocalDateTime now);

    @Query("SELECT COUNT(it) FROM InventoryTransfer it WHERE it.fromShop.id = :shopId " +
            "AND it.status IN ('PENDING', 'APPROVED', 'IN_TRANSIT')")
    long countActiveTransfersFromShop(@Param("shopId") Long shopId);
}