package com.pos_onlineshop.hybrid.dtos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class CreateAccrualRequest {

    /** Caller-supplied, unique - doubles as the GL idempotency key
     * ("ACCRUAL-" + reference), so a retried request replays the original posting. */
    @NotBlank(message = "Reference is required")
    private String reference;

    @NotNull(message = "Accrual date is required")
    private LocalDate accrualDate;

    /** Date on/after which this accrual should be reversed - typically the first day of
     * the next accounting period. Must not be before accrualDate. */
    @NotNull(message = "Reversal date is required")
    private LocalDate reversalDate;

    @NotBlank(message = "Description is required")
    private String description;

    @NotNull(message = "Creator is required")
    private Long createdByUserId;

    /** Must already balance (total debits == total credits) - unlike an opening balance,
     * an accrual has no automatic equity plug. */
    @NotEmpty(message = "An accrual needs at least two lines")
    @Valid
    private List<AccrualLineRequest> lines;
}
