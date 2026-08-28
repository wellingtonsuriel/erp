package com.pos_onlineshop.hybrid.dtos;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ManualJournalResponse {
    private Long id;
    private String reference;
    private String status;
    private LocalDate entryDate;
    private String description;
    private String attachmentReference;

    private Long createdById;
    private String createdByUsername;
    private LocalDateTime createdAt;

    private Long submittedById;
    private String submittedByUsername;
    private LocalDateTime submittedAt;

    private Long approvedById;
    private String approvedByUsername;
    private LocalDateTime approvedAt;

    private Long rejectedById;
    private String rejectedByUsername;
    private LocalDateTime rejectedAt;
    private String rejectionReason;

    private Long postedJournalEntryId;
    private Long postedJournalEntryNumber;

    private List<ManualJournalLineResponse> lines;
}
