package com.pos_onlineshop.hybrid.dtos;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class OpeningBalanceResponse {
    private Long id;
    private String reference;
    private LocalDate entryDate;
    private String description;

    private Long createdById;
    private String createdByUsername;
    private LocalDateTime createdAt;

    private Long postedJournalEntryId;
    private Long postedJournalEntryNumber;

    private List<JournalLineResponse> lines;
}
