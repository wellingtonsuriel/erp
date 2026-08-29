package com.pos_onlineshop.hybrid.dtos;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ApprovalRequestResponse {
    private Long id;
    private String entityType;
    private Long entityId;
    private String action;
    private String details;
    private Long requestedById;
    private String requestedByUsername;
    private LocalDateTime requestedAt;
    private String status;
    private Long decidedById;
    private String decidedByUsername;
    private LocalDateTime decidedAt;
    private String reason;
}
