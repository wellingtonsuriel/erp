package com.pos_onlineshop.hybrid.dtos;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class JournalEntryDetailResponse {
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
    private Long reversalOfEntryId;
    private Long reversalOfEntryNumber;
    private Long reversedByEntryId;
    private Long reversedByEntryNumber;
    private List<JournalLineResponse> lines;
}
