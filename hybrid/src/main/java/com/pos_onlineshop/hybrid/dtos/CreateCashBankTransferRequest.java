package com.pos_onlineshop.hybrid.dtos;

import com.pos_onlineshop.hybrid.enums.CashBankTransferType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class CreateCashBankTransferRequest {

    @NotBlank(message = "Reference number is required")
    private String referenceNumber;

    @NotNull(message = "Transfer type is required")
    private CashBankTransferType transferType;

    @NotNull(message = "Source account is required")
    private Long fromAccountId;

    @NotNull(message = "Destination account is required")
    private Long toAccountId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.0001", message = "Amount must be positive")
    private BigDecimal amount;

    @NotNull(message = "Transfer date is required")
    private LocalDate transferDate;

    private String description;

    @NotNull(message = "Creator is required")
    private Long createdByUserId;
}
