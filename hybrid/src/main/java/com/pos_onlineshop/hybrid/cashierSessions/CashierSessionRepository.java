package com.pos_onlineshop.hybrid.cashierSessions;

import com.pos_onlineshop.hybrid.cashier.Cashier;
import com.pos_onlineshop.hybrid.enums.SessionStatus;
import com.pos_onlineshop.hybrid.shop.Shop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CashierSessionRepository extends JpaRepository<CashierSession, Long> {

    Optional<CashierSession> findByCashierAndStatus(Cashier cashier, SessionStatus status);

    List<CashierSession> findByCashier(Cashier cashier);

    List<CashierSession> findByShopAndStatus(Shop shop, SessionStatus status);

    /**
     * Find all sessions by status
     */
    List<CashierSession> findByStatus(SessionStatus status);

    /**
     * Find active session for a specific cashier
     */
    @Query("SELECT cs FROM CashierSession cs WHERE cs.cashier.id = :cashierId AND cs.status = 'ACTIVE'")
    Optional<CashierSession> findActiveByCashierId(@Param("cashierId") Long cashierId);

    /**
     * Find sessions within a date range
     */
    @Query("SELECT cs FROM CashierSession cs WHERE cs.sessionStart BETWEEN :startDate AND :endDate ORDER BY cs.sessionStart DESC")
    List<CashierSession> findByDateRange(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    /**
     * Find sessions by cashier and date range
     */
    @Query("SELECT cs FROM CashierSession cs WHERE cs.cashier.id = :cashierId AND cs.sessionStart BETWEEN :startDate AND :endDate ORDER BY cs.sessionStart DESC")
    List<CashierSession> findByCashierAndDateRange(
            @Param("cashierId") Long cashierId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    /**
     * Find sessions by shop and date range
     */
    @Query("SELECT cs FROM CashierSession cs WHERE cs.shop.id = :shopId AND cs.sessionStart BETWEEN :startDate AND :endDate ORDER BY cs.sessionStart DESC")
    List<CashierSession> findByShopAndDateRange(
            @Param("shopId") Long shopId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    /**
     * Get total sales for a cashier in date range
     */
    @Query("SELECT SUM(cs.totalSales) FROM CashierSession cs WHERE cs.cashier.id = :cashierId AND cs.sessionStart BETWEEN :startDate AND :endDate")
    BigDecimal getTotalSalesByCashier(
            @Param("cashierId") Long cashierId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    /**
     * Get total sales for a shop in date range
     */
    @Query("SELECT SUM(cs.totalSales) FROM CashierSession cs WHERE cs.shop.id = :shopId AND cs.sessionStart BETWEEN :startDate AND :endDate")
    BigDecimal getTotalSalesByShop(
            @Param("shopId") Long shopId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    /**
     * Count active sessions by shop
     */
    @Query("SELECT COUNT(cs) FROM CashierSession cs WHERE cs.shop.id = :shopId AND cs.status = 'ACTIVE'")
    Long countActiveSessionsByShop(@Param("shopId") Long shopId);

    /**
     * Find all active sessions with cashier and shop details
     */
    @Query("SELECT cs FROM CashierSession cs JOIN FETCH cs.cashier JOIN FETCH cs.shop WHERE cs.status = 'ACTIVE'")
    List<CashierSession> findAllActiveWithDetails();

    /**
     * Get session statistics for a period
     */
    @Query("SELECT " +
            "COUNT(cs) as sessionCount, " +
            "SUM(cs.totalSales) as totalSales, " +
            "SUM(cs.transactionCount) as totalTransactions, " +
            "AVG(cs.totalSales) as averageSales " +
            "FROM CashierSession cs " +
            "WHERE cs.sessionStart BETWEEN :startDate AND :endDate")
    Object[] getSessionStatistics(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );
}