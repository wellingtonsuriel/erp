package com.pos_onlineshop.hybrid.dtos;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PayPayrollRunRequest {

    @NotNull(message = "Acting user is required")
    private Long userId;
}
