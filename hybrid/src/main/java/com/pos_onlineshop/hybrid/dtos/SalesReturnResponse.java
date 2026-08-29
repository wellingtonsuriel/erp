package com.pos_onlineshop.hybrid.dtos;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class SalesReturnResponse {
    private Long id;
    private String returnNumber;
    private Long orderId;
    private Long customerId;
    private String customerName;
    private String reason;
    private LocalDate returnDate;
    private BigDecimal totalRefundAmount;
    private BigDecimal totalTaxReversed;
    private BigDecimal totalCostReversed;

    private Long createdById;
    private String createdByUsername;
    private LocalDateTime createdAt;

    private Long journalEntryId;
    private Long journalEntryNumber;

    private List<SalesReturnLineResponse> lines;
}
