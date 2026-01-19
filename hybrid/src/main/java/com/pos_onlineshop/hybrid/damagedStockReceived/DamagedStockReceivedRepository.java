package com.pos_onlineshop.hybrid.damagedStockReceived;

import com.pos_onlineshop.hybrid.inventoryTransfer.InventoryTransfer;
import com.pos_onlineshop.hybrid.products.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository interface for DamagedStockReceived entity.
 * Provides methods to query and manage damaged stock records.
 */
@Repository
public interface DamagedStockReceivedRepository extends JpaRepository<DamagedStockReceived, Long> {

    /**
     * Find all damaged items for a specific inventory transfer
     *
     * @param transfer The inventory transfer
     * @return List of damaged stock records
     */
    List<DamagedStockReceived> findByTransfer(InventoryTransfer transfer);

    /**
     * Find all damaged items for a specific inventory transfer ID
     *
     * @param transferId The inventory transfer ID
     * @return List of damaged stock records
     */
    @Query("SELECT d FROM DamagedStockReceived d WHERE d.transfer.id = :transferId")
    List<DamagedStockReceived> findByTransferId(@Param("transferId") Long transferId);

    /**
     * Find all damaged items for a specific product
     *
     * @param product  The product
     * @param pageable Pagination information
     * @return Page of damaged stock records
     */
    Page<DamagedStockReceived> findByProduct(Product product, Pageable pageable);

    /**
     * Find all damaged items by severity
     *
     * @param severity The damage severity
     * @param pageable Pagination information
     * @return Page of damaged stock records
     */
    Page<DamagedStockReceived> findBySeverity(DamagedStockReceived.DamageSeverity severity, Pageable pageable);

    /**
     * Find all repairable damaged items
     *
     * @param repairable Whether items are repairable
     * @param pageable   Pagination information
     * @return Page of damaged stock records
     */
    Page<DamagedStockReceived> findByRepairable(Boolean repairable, Pageable pageable);

    /**
     * Find all damaged items with insurance claims
     *
     * @param insuranceClaimed Whether insurance was claimed
     * @param pageable         Pagination information
     * @return Page of damaged stock records
     */
    Page<DamagedStockReceived> findByInsuranceClaimed(Boolean insuranceClaimed, Pageable pageable);

    /**
     * Find damaged items within a date range
     *
     * @param startDate Start date
     * @param endDate   End date
     * @param pageable  Pagination information
     * @return Page of damaged stock records
     */
    @Query("SELECT d FROM DamagedStockReceived d WHERE d.identifiedAt BETWEEN :startDate AND :endDate")
    Page<DamagedStockReceived> findByDateRange(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable
    );

    /**
     * Calculate total damage value for a specific transfer
     *
     * @param transferId The inventory transfer ID
     * @return Total damage value
     */
    @Query("SELECT COALESCE(SUM(d.damagedQuantity * d.unitCost), 0) FROM DamagedStockReceived d WHERE d.transfer.id = :transferId")
    BigDecimal calculateTotalDamageValueByTransfer(@Param("transferId") Long transferId);

    /**
     * Calculate total damage value for a specific product
     *
     * @param productId The product ID
     * @return Total damage value
     */
    @Query("SELECT COALESCE(SUM(d.damagedQuantity * d.unitCost), 0) FROM DamagedStockReceived d WHERE d.product.id = :productId")
    BigDecimal calculateTotalDamageValueByProduct(@Param("productId") Long productId);

    /**
     * Count damaged items by severity
     *
     * @param severity The damage severity
     * @return Count of damaged items
     */
    long countBySeverity(DamagedStockReceived.DamageSeverity severity);

    /**
     * Find all damaged items with insurance claim number
     *
     * @param insuranceClaimNumber The insurance claim number
     * @return List of damaged stock records
     */
    List<DamagedStockReceived> findByInsuranceClaimNumber(String insuranceClaimNumber);

    /**
     * Get summary statistics for damaged stock within a date range
     *
     * @param startDate Start date
     * @param endDate   End date
     * @return List of objects containing summary data
     */
    @Query("SELECT d.severity as severity, " +
            "COUNT(d) as count, " +
            "SUM(d.damagedQuantity) as totalQuantity, " +
            "SUM(d.damagedQuantity * d.unitCost) as totalValue " +
            "FROM DamagedStockReceived d " +
            "WHERE d.identifiedAt BETWEEN :startDate AND :endDate " +
            "GROUP BY d.severity")
    List<Object[]> getDamageSummaryByDateRange(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    /**
     * Find damaged items for transfers from a specific shop
     *
     * @param shopId   The shop ID
     * @param pageable Pagination information
     * @return Page of damaged stock records
     */
    @Query("SELECT d FROM DamagedStockReceived d WHERE d.transfer.fromShop.id = :shopId")
    Page<DamagedStockReceived> findByFromShopId(@Param("shopId") Long shopId, Pageable pageable);

    /**
     * Find damaged items for transfers to a specific shop
     *
     * @param shopId   The shop ID
     * @param pageable Pagination information
     * @return Page of damaged stock records
     */
    @Query("SELECT d FROM DamagedStockReceived d WHERE d.transfer.toShop.id = :shopId")
    Page<DamagedStockReceived> findByToShopId(@Param("shopId") Long shopId, Pageable pageable);

    /**
     * Get top products with most damage by quantity
     *
     * @param pageable Pagination information (use for limiting results)
     * @return List of objects containing product and damage statistics
     */
    @Query("SELECT d.product as product, " +
            "SUM(d.damagedQuantity) as totalDamagedQuantity, " +
            "COUNT(d) as damageIncidents, " +
            "SUM(d.damagedQuantity * d.unitCost) as totalDamageValue " +
            "FROM DamagedStockReceived d " +
            "GROUP BY d.product " +
            "ORDER BY SUM(d.damagedQuantity) DESC")
    List<Object[]> getTopDamagedProducts(Pageable pageable);
}
