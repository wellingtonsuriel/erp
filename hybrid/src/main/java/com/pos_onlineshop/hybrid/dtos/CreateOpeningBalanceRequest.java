package com.pos_onlineshop.hybrid.dtos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class CreateOpeningBalanceRequest {

    /** Caller-supplied, unique - doubles as the GL idempotency key, so a retried request
     * with the same reference replays the original posting instead of double-posting. */
    @NotBlank(message = "Reference is required")
    private String reference;

    @NotNull(message = "Entry date is required")
    private LocalDate entryDate;

    @NotBlank(message = "Description is required")
    private String description;

    @NotNull(message = "Creator is required")
    private Long createdByUserId;

    /** The known side of the opening balance (e.g. existing AR/AP/Inventory/Cash balances).
     * The balancing line against Opening Balance Equity (3900) is computed and appended
     * automatically - do not include a 3900 line here. */
    @NotEmpty(message = "At least one opening balance line is required")
    @Valid
    private List<OpeningBalanceLineRequest> lines;
}
