package com.pos_onlineshop.hybrid.enums;

import lombok.Getter;

@Getter
public enum TransferStatus {
    PENDING("Pending Approval"),
    APPROVED("Approved"),
    IN_TRANSIT("In Transit"),
    RECEIVED("Received"),
    COMPLETED("Completed"),
    CANCELLED("Cancelled");

    private final String displayName;

    TransferStatus(String displayName) {
        this.displayName = displayName;
    }

    public boolean isActive() {
        return this != COMPLETED && this != CANCELLED;
    }

    public boolean isCompleted() {
        return this == COMPLETED || this == CANCELLED;
    }
}
