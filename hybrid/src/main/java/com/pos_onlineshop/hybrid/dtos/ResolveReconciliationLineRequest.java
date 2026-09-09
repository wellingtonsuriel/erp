package com.pos_onlineshop.hybrid.dtos;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ResolveReconciliationLineRequest {

    @NotBlank(message = "Resolution reason is required")
    private String resolutionReason;

    // Ignored server-side: the resolver is always the authenticated caller's username (see
    // ControlAccountReconciliationController's class comment), never this request-body field.
    private String resolvedBy;
}
