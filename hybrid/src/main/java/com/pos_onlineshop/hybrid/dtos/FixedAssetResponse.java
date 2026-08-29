package com.pos_onlineshop.hybrid.dtos;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class FixedAssetResponse {
    private Long id;
    private String assetNumber;
    private String name;
    private Long categoryId;
    private String categoryName;
    private Long shopId;
    private String shopName;
    private LocalDate acquisitionDate;
    private BigDecimal acquisitionCost;
    private Integer usefulLifeMonths;
    private BigDecimal residualValue;
    private String depreciationMethod;
    private BigDecimal accumulatedDepreciation;
    private BigDecimal netBookValue;
    private String status;
    private LocalDateTime createdAt;
    private Long acquisitionJournalEntryId;
    private Long acquisitionJournalEntryNumber;
}
