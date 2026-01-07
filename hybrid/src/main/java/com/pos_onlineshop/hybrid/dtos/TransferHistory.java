package com.pos_onlineshop.hybrid.dtos;


import com.pos_onlineshop.hybrid.enums.TransferStatus;
import com.pos_onlineshop.hybrid.enums.TransferPriority;
import com.pos_onlineshop.hybrid.enums.TransferType;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO representing the complete history and timeline of a transfer
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransferHistory {

    // Basic transfer info
    private String transferNumber;
    private TransferType transferType;
    private TransferPriority priority;
    private String notes;

    // Shop information
    private String fromShopName;
    private String toShopName;

    // Timeline - Initiation
    private String initiatedBy;
    private LocalDateTime requestedAt;

    // Timeline - Approval
    private String approvedBy;
    private LocalDateTime approvedAt;
    private Long approvalDurationMinutes;

    // Timeline - Shipping
    private String shippedBy;
    private LocalDateTime shippedAt;
    private LocalDateTime expectedDelivery;
    private Long shippingPreparationMinutes;

    // Timeline - Receiving
    private String receivedBy;
    private LocalDateTime receivedAt;
    private Long transitDurationHours;

    // Timeline - Completion/Cancellation
    private LocalDateTime completedAt;
    private LocalDateTime cancelledAt;
    private String cancellationReason;

    // Current status
    private TransferStatus currentStatus;
    private Boolean isOverdue;
    private Boolean isUrgent;

    // Performance metrics
    private Long totalTransferDurationHours;
    private Long totalProcessingDurationHours;
    private String performanceRating; // EXCELLENT, GOOD, AVERAGE, POOR, DELAYED

    // Status history
    private List<StatusChange> statusHistory;

    // Helper methods for calculated fields
    public Long getApprovalDurationMinutes() {
        if (requestedAt != null && approvedAt != null) {
            return java.time.Duration.between(requestedAt, approvedAt).toMinutes();
        }
        return null;
    }

    public Long getShippingPreparationMinutes() {
        if (approvedAt != null && shippedAt != null) {
            return java.time.Duration.between(approvedAt, shippedAt).toMinutes();
        }
        return null;
    }

    public Long getTransitDurationHours() {
        if (shippedAt != null && receivedAt != null) {
            return java.time.Duration.between(shippedAt, receivedAt).toHours();
        }
        return null;
    }

    public Long getTotalTransferDurationHours() {
        if (requestedAt != null && receivedAt != null) {
            return java.time.Duration.between(requestedAt, receivedAt).toHours();
        }
        return null;
    }

    public String getPerformanceRating() {
        Long totalHours = getTotalTransferDurationHours();
        if (totalHours == null) return "PENDING";

        // Performance rating based on total duration and priority
        if (priority == TransferPriority.CRITICAL || priority == TransferPriority.URGENT) {
            if (totalHours <= 4) return "EXCELLENT";
            if (totalHours <= 8) return "GOOD";
            if (totalHours <= 24) return "AVERAGE";
            return "DELAYED";
        } else {
            if (totalHours <= 24) return "EXCELLENT";
            if (totalHours <= 48) return "GOOD";
            if (totalHours <= 72) return "AVERAGE";
            return "DELAYED";
        }
    }

    // Inner class for status change tracking
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StatusChange {
        private TransferStatus fromStatus;
        private TransferStatus toStatus;
        private LocalDateTime changedAt;
        private String changedBy;
        private String reason;
        private Long durationInPreviousStatusMinutes;
    }
}