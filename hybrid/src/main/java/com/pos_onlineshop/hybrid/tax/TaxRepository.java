package com.pos_onlineshop.hybrid.tax;

import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.enums.TaxCalculationType;
import com.pos_onlineshop.hybrid.enums.TaxNature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Tax entity.
 * Provides data access operations for tax management.
 */
@Repository
public interface TaxRepository extends JpaRepository<Tax, Long> {

    /**
     * Find all active taxes.
     */
    List<Tax> findByActive(Boolean active);

    /**
     * Find taxes by nature.
     */
    List<Tax> findByTaxNature(TaxNature taxNature);

    /**
     * Find taxes by calculation type.
     */
    List<Tax> findByTaxCalculationType(TaxCalculationType taxCalculationType);

    /**
     * Find active taxes by nature.
     */
    List<Tax> findByTaxNatureAndActive(TaxNature taxNature, Boolean active);

    /**
     * Find active taxes by calculation type.
     */
    List<Tax> findByTaxCalculationTypeAndActive(TaxCalculationType taxCalculationType, Boolean active);

    /**
     * Find taxes by currency (useful for FIXED taxes).
     */
    List<Tax> findByCurrency(Currency currency);

    /**
     * Find a tax by name.
     */
    Optional<Tax> findByTaxName(String taxName);

    /**
     * Find taxes by name containing (case-insensitive search).
     */
    @Query("SELECT t FROM Tax t WHERE LOWER(t.taxName) LIKE LOWER(CONCAT('%', :name, '%'))")
    List<Tax> findByTaxNameContaining(@Param("name") String name);

    /**
     * Check if a tax with the given name exists.
     */
    boolean existsByTaxName(String taxName);
}
