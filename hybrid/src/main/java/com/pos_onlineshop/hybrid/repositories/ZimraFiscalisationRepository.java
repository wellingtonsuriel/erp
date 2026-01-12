package com.pos_onlineshop.hybrid.repositories;

import com.pos_onlineshop.hybrid.enums.FiscalStatus;
import com.pos_onlineshop.hybrid.shop.Shop;
import com.pos_onlineshop.hybrid.zimra.ZimraFiscalisation;
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
public interface ZimraFiscalisationRepository extends JpaRepository<ZimraFiscalisation, Long> {

    Optional<ZimraFiscalisation> findByFiscalCode(String fiscalCode);

    Optional<ZimraFiscalisation> findByReceiptNumber(String receiptNumber);

    Optional<ZimraFiscalisation> findByVerificationCode(String verificationCode);

    Optional<ZimraFiscalisation> findByOrderId(Long orderId);

    Optional<ZimraFiscalisation> findBySaleId(Long saleId);

    List<ZimraFiscalisation> findByShop(Shop shop);

    Page<ZimraFiscalisation> findByShop(Shop shop, Pageable pageable);

    List<ZimraFiscalisation> findByStatus(FiscalStatus status);

    Page<ZimraFiscalisation> findByStatus(FiscalStatus status, Pageable pageable);

    List<ZimraFiscalisation> findByShopAndStatus(Shop shop, FiscalStatus status);

    @Query("SELECT z FROM ZimraFiscalisation z WHERE z.fiscalDate BETWEEN :startDate AND :endDate")
    List<ZimraFiscalisation> findByDateRange(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    @Query("SELECT z FROM ZimraFiscalisation z WHERE z.shop = :shop AND z.fiscalDate BETWEEN :startDate AND :endDate")
    List<ZimraFiscalisation> findByShopAndDateRange(
        @Param("shop") Shop shop,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    @Query("SELECT z FROM ZimraFiscalisation z WHERE z.shop = :shop AND z.fiscalDate BETWEEN :startDate AND :endDate")
    Page<ZimraFiscalisation> findByShopAndDateRange(
        @Param("shop") Shop shop,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate,
        Pageable pageable
    );

    @Query("SELECT COALESCE(SUM(z.totalAmount), 0) FROM ZimraFiscalisation z WHERE z.shop = :shop AND z.status = 'FISCALISED'")
    Double getTotalFiscalisedAmountByShop(@Param("shop") Shop shop);

    @Query("SELECT COALESCE(SUM(z.totalAmount), 0) FROM ZimraFiscalisation z WHERE z.shop = :shop AND z.status = 'FISCALISED' AND z.fiscalDate BETWEEN :startDate AND :endDate")
    Double getTotalFiscalisedAmountByShopAndDateRange(
        @Param("shop") Shop shop,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    @Query("SELECT COUNT(z) FROM ZimraFiscalisation z WHERE z.shop = :shop AND z.fiscalDate >= :date AND z.status = 'FISCALISED'")
    Long countFiscalisedTransactionsSince(@Param("shop") Shop shop, @Param("date") LocalDateTime date);

    @Query("SELECT z FROM ZimraFiscalisation z WHERE z.status = 'FAILED' AND z.retryCount < 3 ORDER BY z.createdAt ASC")
    List<ZimraFiscalisation> findFailedTransactionsForRetry();

    @Query("SELECT z FROM ZimraFiscalisation z WHERE z.shop = :shop ORDER BY z.fiscalDate DESC")
    List<ZimraFiscalisation> findRecentByShop(@Param("shop") Shop shop, Pageable pageable);
}
