package com.pos_onlineshop.hybrid.dtos;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Summary shape used for the list endpoint - no lines, so a listing stays a single query. */
@Data
@Builder
public class JournalEntryResponse {
    private Long id;
    private Long entryNumber;
    private String idempotencyKey;
    private LocalDate entryDate;
    private String accountingPeriodName;
    private String description;
    private String sourceModule;
    private String sourceReferenceType;
    private Long sourceReferenceId;
    private String status;
    private String postedBy;
    private LocalDateTime postedAt;
    private BigDecimal totalDebits;
    private BigDecimal totalCredits;
    private Long reversalOfEntryId;
    private Long reversedByEntryId;
}
