package com.pos_onlineshop.hybrid.dtos;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Compares the legacy AccountancyEntry ledger's totals against the real GL's totals for the
 * same business events (joined on referenceType/referenceId), per Phase POS/OrderService's
 * "parallel posting until the new ledger's totals are verified against the old one" plan.
 * Never adjusts either side to force a match - see ControlAccountReconciliationReport's
 * identical rule.
 */
@Data
@Builder
public class LegacyGlReconciliationReport {

    private LocalDate fromDate;
    private LocalDate toDate;

    private int totalCompared;
    private int totalMatched;
    private int totalUnmatched;

    private List<Line> lines;

    @Data
    @Builder
    public static class Line {
        private String referenceType;
        private Long referenceId;
        private BigDecimal legacyAmount;
        private BigDecimal glAmount;
        private BigDecimal variance;
        private boolean matched;
        /** True when this reference has legacy entries but no matching GL entry, or vice
         * versa - distinct from a matched-but-wrong-amount variance. */
        private boolean missingOnOneSide;
    }
}
