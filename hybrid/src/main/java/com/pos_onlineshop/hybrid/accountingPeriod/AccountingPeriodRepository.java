package com.pos_onlineshop.hybrid.accountingPeriod;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface AccountingPeriodRepository extends JpaRepository<AccountingPeriod, Long> {

    Optional<AccountingPeriod> findByStartDateLessThanEqualAndEndDateGreaterThanEqual(
            LocalDate date1, LocalDate date2);

    default Optional<AccountingPeriod> findContaining(LocalDate date) {
        return findByStartDateLessThanEqualAndEndDateGreaterThanEqual(date, date);
    }

    Optional<AccountingPeriod> findByName(String name);
}
