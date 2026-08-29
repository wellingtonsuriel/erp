package com.pos_onlineshop.hybrid.dtos;

import com.pos_onlineshop.hybrid.enums.PaymentMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class ProcessPayrollRequest {

    @NotBlank(message = "Run number is required")
    private String runNumber;

    @NotNull(message = "Period start is required")
    private LocalDate periodStart;

    @NotNull(message = "Period end is required")
    private LocalDate periodEnd;

    @NotNull(message = "Pay date is required")
    private LocalDate payDate;

    @NotNull(message = "Currency is required")
    private Long currencyId;

    private PaymentMethod paymentMethod;

    @NotNull(message = "Acting user is required")
    private Long userId;
}
