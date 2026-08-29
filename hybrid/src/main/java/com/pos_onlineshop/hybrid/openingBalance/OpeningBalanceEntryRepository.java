package com.pos_onlineshop.hybrid.openingBalance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OpeningBalanceEntryRepository extends JpaRepository<OpeningBalanceEntry, Long> {
    Optional<OpeningBalanceEntry> findByReference(String reference);

    boolean existsByReference(String reference);

    List<OpeningBalanceEntry> findAllByOrderByIdDesc();
}
