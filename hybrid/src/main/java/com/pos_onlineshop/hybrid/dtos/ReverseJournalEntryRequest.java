package com.pos_onlineshop.hybrid.dtos;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

@Data
public class ReverseJournalEntryRequest {

    @NotBlank(message = "A reason is required to reverse a posted journal entry")
    private String reason;

    /** Defaults to today if omitted. Must fall in an OPEN accounting period. */
    private LocalDate reversalDate;

    private String postedBy;
}
