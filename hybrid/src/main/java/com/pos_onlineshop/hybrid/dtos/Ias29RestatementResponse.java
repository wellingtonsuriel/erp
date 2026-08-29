package com.pos_onlineshop.hybrid.dtos;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class Ias29RestatementResponse {
    private Long id;
    private Long fixedAssetId;
    private String assetNumber;
    private LocalDate restatementDate;
    private BigDecimal priorRestatedCost;
    private BigDecimal newRestatedCost;
    private BigDecimal priorRestatedAccumulatedDepreciation;
    private BigDecimal newRestatedAccumulatedDepreciation;
    private BigDecimal netAdjustment;
    private LocalDateTime createdAt;
    private Long postedJournalEntryId;
    private Long postedJournalEntryNumber;
    private boolean reversed;
    private Long reversalJournalEntryId;
    private Long reversalJournalEntryNumber;
    private String reversalReason;
    private LocalDateTime reversedAt;
}
