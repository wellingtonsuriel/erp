package com.pos_onlineshop.hybrid.dtos;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class AssetDisposalResponse {
    private Long id;
    private Long assetId;
    private String assetNumber;
    private LocalDate disposalDate;
    private BigDecimal proceedsAmount;
    private BigDecimal netBookValueAtDisposal;
    private BigDecimal gainLoss;
    private String reason;
    private LocalDateTime createdAt;
    private Long postedJournalEntryId;
    private Long postedJournalEntryNumber;
}
