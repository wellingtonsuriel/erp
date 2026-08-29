package com.pos_onlineshop.hybrid.dtos;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Mirrors CustomerStatementReport - see its class comment. Here a debit is an invoice
 * (increasing what we owe the supplier) and a credit is a payment or debit note (decreasing
 * it) - the naming follows SupplierInvoice.applyPayment's own convention, not the AP
 * control account's normal balance. */
@Data
@Builder
public class SupplierStatementReport {

    private Long supplierId;
    private String supplierName;
    private LocalDate fromDate;
    private LocalDate toDate;
    private BigDecimal openingBalance;
    private List<Line> lines;
    private BigDecimal closingBalance;

    @Data
    @Builder
    public static class Line {
        private LocalDate date;
        /** INVOICE, PAYMENT, or DEBIT_NOTE. */
        private String type;
        private String reference;
        private String description;
        private BigDecimal debit;
        private BigDecimal credit;
        private BigDecimal runningBalance;
    }
}
