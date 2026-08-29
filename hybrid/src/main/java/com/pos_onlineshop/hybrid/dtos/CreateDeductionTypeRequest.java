package com.pos_onlineshop.hybrid.dtos;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateDeductionTypeRequest {

    @NotBlank(message = "Name is required")
    private String name;

    @NotNull(message = "percentage must be specified")
    private Boolean percentage;

    /** Required when percentage=true - a percentage of gross pay (e.g. 5.00 for 5%). */
    @DecimalMin(value = "0.0", message = "Rate cannot be negative")
    private BigDecimal rate;

    /** Required when percentage=false - a flat amount per payroll run. */
    @DecimalMin(value = "0.0", message = "Fixed amount cannot be negative")
    private BigDecimal fixedAmount;
}
