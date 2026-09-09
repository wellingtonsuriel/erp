package com.pos_onlineshop.hybrid.dtos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class CreateManualJournalRequest {

    @NotNull(message = "Entry date is required")
    private LocalDate entryDate;

    @NotBlank(message = "Description is required")
    private String description;

    // Ignored server-side: the creator is always the authenticated caller (see
    // ManualJournalController/ManualJournalService's class comments), never this request-body field.
    private Long createdByUserId;

    private String attachmentReference;

    @NotEmpty(message = "A manual journal needs at least two lines")
    @Valid
    private List<ManualJournalLineRequest> lines;
}
