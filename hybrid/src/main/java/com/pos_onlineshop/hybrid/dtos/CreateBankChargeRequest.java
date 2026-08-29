package com.pos_onlineshop.hybrid.dtos;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class CreateBankChargeRequest {

    @NotBlank(message = "Reference number is required")
    private String referenceNumber;

    @NotNull(message = "Bank account is required")
    private Long bankAccountId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.0001", message = "Amount must be positive")
    private BigDecimal amount;

    @NotNull(message = "Charge date is required")
    private LocalDate chargeDate;

    private String description;

    @NotNull(message = "Creator is required")
    private Long createdByUserId;
}
