package com.pos_onlineshop.hybrid.dtos;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SalesReturnLineRequest {

    @NotNull(message = "Order line is required")
    private Long orderLineId;

    @NotNull(message = "Quantity returned is required")
    @Min(value = 1, message = "Quantity returned must be at least 1")
    private Integer quantityReturned;
}
