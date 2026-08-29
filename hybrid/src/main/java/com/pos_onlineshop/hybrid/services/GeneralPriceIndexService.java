package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.generalPriceIndex.GeneralPriceIndex;
import com.pos_onlineshop.hybrid.generalPriceIndex.GeneralPriceIndexRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * IAS 29 (Financial Reporting in Hyperinflationary Economies) EXTENSION POINT - not a full
 * restatement implementation. What this provides: recording published general price index
 * readings (e.g. monthly CPI) and computing the conversion factor between any two dates,
 * which is the one calculation every IAS 29 restatement line ultimately needs
 * (restatedAmount = historicalAmount * indexAtRestatementDate / indexAtAcquisitionDate).
 * Account.monetary already classifies which balance-sheet accounts are subject to
 * restatement at all (monetary items like cash/AR/AP are never restated).
 *
 * What is deliberately NOT built here, because it needs data this system does not yet
 * track: restating a non-monetary account's actual balance requires knowing the
 * acquisition/transaction date of every component of that balance (e.g. each inventory
 * lot's receipt date, each fixed asset's purchase date) rather than just its current total -
 * JournalLine has no such per-line "restate as of" date today. Computing the net monetary
 * gain/loss for a period (the P&L consequence of holding a net monetary position during
 * inflation) requires tracking the net monetary position through the period, not just at
 * its endpoints. Both are real, substantial follow-on work once (and if) this business
 * actually needs full hyperinflationary reporting - this service is the foundation they
 * would be built on, not a placeholder that already produces restated figures.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class GeneralPriceIndexService {

    private static final int FACTOR_SCALE = 8;

    private final GeneralPriceIndexRepository generalPriceIndexRepository;

    public GeneralPriceIndex recordIndexValue(LocalDate indexDate, BigDecimal indexValue, String source) {
        if (indexValue == null || indexValue.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Index value must be positive");
        }
        if (generalPriceIndexRepository.existsByIndexDate(indexDate)) {
            throw new IllegalArgumentException("A general price index reading already exists for " + indexDate);
        }
        GeneralPriceIndex saved = generalPriceIndexRepository.save(GeneralPriceIndex.builder()
                .indexDate(indexDate)
                .indexValue(indexValue)
                .source(source)
                .build());
        log.info("Recorded general price index {} = {} (source: {})", indexDate, indexValue, source);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<GeneralPriceIndex> findAll() {
        return generalPriceIndexRepository.findAllByOrderByIndexDateDesc();
    }

    /** The conversion factor to restate an amount from fromDate's price level to toDate's:
     * indexAsAt(toDate) / indexAsAt(fromDate). Each date resolves to the most recently
     * published reading on or before it - see the repository query's comment for why. */
    @Transactional(readOnly = true)
    public BigDecimal getConversionFactor(LocalDate fromDate, LocalDate toDate) {
        BigDecimal fromIndex = indexAsAt(fromDate);
        BigDecimal toIndex = indexAsAt(toDate);
        return toIndex.divide(fromIndex, FACTOR_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal indexAsAt(LocalDate date) {
        return generalPriceIndexRepository.findFirstByIndexDateLessThanEqualOrderByIndexDateDesc(date)
                .map(GeneralPriceIndex::getIndexValue)
                .orElseThrow(() -> new IllegalStateException(
                        "No general price index reading is recorded on or before " + date));
    }
}
