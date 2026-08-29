package com.pos_onlineshop.hybrid.dtos;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class DisposeAssetRequest {

    @NotNull(message = "Disposal date is required")
    private LocalDate disposalDate;

    @NotNull(message = "Proceeds amount is required (0 for a scrapped/written-off asset)")
    @DecimalMin(value = "0.00", message = "Proceeds cannot be negative")
    private BigDecimal proceedsAmount;

    private String reason;
}
