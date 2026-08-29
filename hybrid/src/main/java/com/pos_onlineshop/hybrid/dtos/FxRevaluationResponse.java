package com.pos_onlineshop.hybrid.dtos;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class FxRevaluationResponse {
    private Long id;
    private String invoiceType;
    private Long invoiceId;
    private String invoiceNumber;
    private LocalDate revaluationDate;
    private BigDecimal priorRate;
    private BigDecimal newRate;
    private BigDecimal outstandingAmount;
    private BigDecimal unrealizedGainLoss;
    private LocalDateTime createdAt;
    private Long postedJournalEntryId;
    private Long postedJournalEntryNumber;
}
