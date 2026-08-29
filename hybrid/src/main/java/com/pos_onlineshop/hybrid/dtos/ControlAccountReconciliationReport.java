package com.pos_onlineshop.hybrid.dtos;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Compares each control account's GL balance against its operational subledger, per Phase J.
 * Never adjusts either side to force a match - a variance is reported, not silently
 * corrected, so a real defect (a posting bypassing the subledger, or vice versa) stays
 * visible instead of being papered over.
 */
@Data
@Builder
public class ControlAccountReconciliationReport {

    private LocalDate asOfDate;

    private List<Line> lines;

    @Data
    @Builder
    public static class Line {
        private String accountCode;
        private String accountName;
        private String subledgerName;
        private BigDecimal glBalance;
        private BigDecimal subledgerBalance;
        private BigDecimal variance;
        private boolean matched;
        /** Explains what the subledger side represents and any known scope limitation, e.g.
         * inventory valuation being a live snapshot rather than as-of-date. */
        private String note;
    }
}
