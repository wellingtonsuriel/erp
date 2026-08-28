package com.pos_onlineshop.hybrid.dtos;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class ArAgingReport {
    private LocalDate asOfDate;
    private List<Line> lines;
    /** Bucket name -> total outstanding, in insertion order Current/1-30/31-60/61-90/90+. */
    private Map<String, BigDecimal> bucketTotals;
    private BigDecimal totalOutstanding;

    @Data
    @Builder
    public static class Line {
        private Long customerId;
        private String customerName;
        private Long invoiceId;
        private String invoiceNumber;
        private LocalDate invoiceDate;
        private LocalDate dueDate;
        private BigDecimal originalAmount;
        private BigDecimal outstandingAmount;
        private long daysOverdue;
        private String bucket;
    }
}
