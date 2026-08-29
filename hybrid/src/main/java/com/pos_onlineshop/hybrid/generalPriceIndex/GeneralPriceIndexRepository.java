package com.pos_onlineshop.hybrid.generalPriceIndex;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface GeneralPriceIndexRepository extends JpaRepository<GeneralPriceIndex, Long> {
    boolean existsByIndexDate(LocalDate indexDate);

    List<GeneralPriceIndex> findAllByOrderByIndexDateDesc();

    /** The most recent published reading on or before the given date - the standard "index
     * as at" lookup, since a reading is not published for every single calendar date. */
    Optional<GeneralPriceIndex> findFirstByIndexDateLessThanEqualOrderByIndexDateDesc(LocalDate onOrBefore);
}
