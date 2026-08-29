package com.pos_onlineshop.hybrid.dtos;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Opening balance, every transaction within [fromDate, toDate], and a running balance -
 * matches CustomerInvoice's own applyPayment sign convention (a debit increases what the
 * customer owes, a credit decreases it). */
@Data
@Builder
public class CustomerStatementReport {

    private Long customerId;
    private String customerName;
    private LocalDate fromDate;
    private LocalDate toDate;
    private BigDecimal openingBalance;
    private List<Line> lines;
    private BigDecimal closingBalance;

    @Data
    @Builder
    public static class Line {
        private LocalDate date;
        /** INVOICE, RECEIPT, or CREDIT_NOTE. */
        private String type;
        private String reference;
        private String description;
        private BigDecimal debit;
        private BigDecimal credit;
        private BigDecimal runningBalance;
    }
}
