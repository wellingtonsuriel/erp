package com.pos_onlineshop.hybrid.currency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface CurrencyRepository extends JpaRepository<Currency, Long> {

    Optional<Currency> findByCode(String code);

    Optional<Currency> findByBaseCurrencyTrue();

    List<Currency> findByActiveTrue();

    boolean existsByCode(String code);

    @Query("SELECT c FROM Currency c WHERE c.active = true ORDER BY c.displayOrder, c.code")
    List<Currency> findAllActiveOrdered();
}

