package com.pos_onlineshop.hybrid.dtos;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class AccrualResponse {
    private Long id;
    private String reference;
    private LocalDate accrualDate;
    private LocalDate reversalDate;
    private String description;
    private String status;

    private Long createdById;
    private String createdByUsername;
    private LocalDateTime createdAt;

    private Long postedJournalEntryId;
    private Long postedJournalEntryNumber;

    private Long reversalJournalEntryId;
    private Long reversalJournalEntryNumber;
    private LocalDateTime reversedAt;

    private List<JournalLineResponse> lines;
}
