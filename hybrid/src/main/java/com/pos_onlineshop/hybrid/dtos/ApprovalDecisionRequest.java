package com.pos_onlineshop.hybrid.dtos;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ApprovalDecisionRequest {

    @NotNull(message = "Deciding user is required")
    private Long userId;

    private String reason;
}
