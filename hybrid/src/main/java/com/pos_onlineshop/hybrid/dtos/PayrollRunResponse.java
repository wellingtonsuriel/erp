package com.pos_onlineshop.hybrid.dtos;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class PayrollRunResponse {
    private Long id;
    private String runNumber;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private LocalDate payDate;
    private String currencyCode;
    private BigDecimal totalGrossPay;
    private BigDecimal totalDeductions;
    private BigDecimal totalNetPay;
    private String paymentMethod;
    private String status;

    private Long createdById;
    private String createdByUsername;
    private LocalDateTime createdAt;

    private Long accrualJournalEntryId;
    private Long accrualJournalEntryNumber;
    private Long paymentJournalEntryId;
    private Long paymentJournalEntryNumber;
    private LocalDateTime paidAt;

    private List<PayslipResponse> payslips;
}
