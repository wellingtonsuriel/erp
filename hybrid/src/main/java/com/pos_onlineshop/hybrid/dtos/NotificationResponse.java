package com.pos_onlineshop.hybrid.dtos;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class NotificationResponse {
    private Long id;
    private String type;
    private String title;
    private String message;
    private String referenceType;
    private Long referenceId;
    private boolean read;
    private LocalDateTime createdAt;
    private LocalDateTime readAt;
}
