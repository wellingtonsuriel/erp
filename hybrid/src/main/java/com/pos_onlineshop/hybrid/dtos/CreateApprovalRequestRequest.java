package com.pos_onlineshop.hybrid.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateApprovalRequestRequest {

    @NotBlank(message = "Entity type is required")
    private String entityType;

    @NotNull(message = "Entity id is required")
    private Long entityId;

    @NotBlank(message = "Action is required")
    private String action;

    private String details;

    @NotNull(message = "Requester is required")
    private Long requestedByUserId;
}
