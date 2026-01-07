package com.pos_onlineshop.hybrid.exchangeRate;

import com.pos_onlineshop.hybrid.currency.Currency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, Long> {

    @Query("SELECT er FROM ExchangeRate er WHERE er.fromCurrency = :from AND er.toCurrency = :to " +
            "AND er.active = true AND er.effectiveDate <= :date " +
            "AND (er.expiryDate IS NULL OR er.expiryDate > :date) " +
            "ORDER BY er.effectiveDate DESC")
    Optional<ExchangeRate> findCurrentRate(@Param("from") Currency from,
                                           @Param("to") Currency to,
                                           @Param("date") LocalDateTime date);

    List<ExchangeRate> findByFromCurrencyAndActiveTrue(Currency fromCurrency);

    @Query("SELECT er FROM ExchangeRate er WHERE er.effectiveDate BETWEEN :startDate AND :endDate")
    List<ExchangeRate> findByDateRange(@Param("startDate") LocalDateTime startDate,
                                       @Param("endDate") LocalDateTime endDate);
}