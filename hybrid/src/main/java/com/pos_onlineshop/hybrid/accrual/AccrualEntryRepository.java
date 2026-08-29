package com.pos_onlineshop.hybrid.accrual;

import com.pos_onlineshop.hybrid.enums.AccrualStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AccrualEntryRepository extends JpaRepository<AccrualEntry, Long> {
    Optional<AccrualEntry> findByReference(String reference);

    boolean existsByReference(String reference);

    List<AccrualEntry> findAllByOrderByIdDesc();

    List<AccrualEntry> findByStatusAndReversalDateLessThanEqual(AccrualStatus status, LocalDate reversalDate);
}
