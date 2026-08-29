package com.pos_onlineshop.hybrid.accountingPeriod;

import com.pos_onlineshop.hybrid.enums.PeriodStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AccountingPeriodRepository extends JpaRepository<AccountingPeriod, Long> {

    Optional<AccountingPeriod> findByStartDateLessThanEqualAndEndDateGreaterThanEqual(
            LocalDate date1, LocalDate date2);

    default Optional<AccountingPeriod> findContaining(LocalDate date) {
        return findByStartDateLessThanEqualAndEndDateGreaterThanEqual(date, date);
    }

    Optional<AccountingPeriod> findByName(String name);

    /** Used to enforce closing periods in chronological order - see AccountingPeriodService. */
    boolean existsByStartDateBeforeAndStatus(LocalDate date, PeriodStatus status);

    /** Used to block reopening a period once a later one has already closed on top of it. */
    boolean existsByStartDateAfterAndStatusIn(LocalDate date, List<PeriodStatus> statuses);
}
