package com.pos_onlineshop.hybrid.dtos;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ResolveReconciliationLineRequest {

    @NotBlank(message = "Resolution reason is required")
    private String resolutionReason;

    @NotBlank(message = "Resolved by is required")
    private String resolvedBy;
}
