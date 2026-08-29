package com.pos_onlineshop.hybrid.dtos;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class AssetDepreciationResponse {
    private Long id;
    private Long assetId;
    private String assetNumber;
    private LocalDate periodDate;
    private BigDecimal amount;
    private BigDecimal accumulatedDepreciationAfter;
    private LocalDateTime createdAt;
    private Long postedJournalEntryId;
    private Long postedJournalEntryNumber;
}
