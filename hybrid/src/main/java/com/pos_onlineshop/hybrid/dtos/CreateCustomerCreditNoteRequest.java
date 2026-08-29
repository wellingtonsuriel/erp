package com.pos_onlineshop.hybrid.dtos;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class CreateCustomerCreditNoteRequest {

    @NotBlank(message = "Credit note number is required")
    private String creditNoteNumber;

    @NotNull(message = "Invoice is required")
    private Long invoiceId;

    @NotNull(message = "Currency is required")
    private Long currencyId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.0001", message = "Amount must be positive")
    private BigDecimal amount;

    @NotBlank(message = "Reason is required")
    private String reason;

    @NotNull(message = "Issue date is required")
    private LocalDate issueDate;
}
