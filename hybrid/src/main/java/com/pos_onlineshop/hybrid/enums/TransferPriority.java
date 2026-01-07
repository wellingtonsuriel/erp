package com.pos_onlineshop.hybrid.enums;

import lombok.Getter;

@Getter
public enum TransferPriority {
    LOW("Low Priority"),
    NORMAL("Normal Priority"),
    HIGH("High Priority"),
    URGENT("Urgent"),
    CRITICAL("Critical");

    private final String displayName;

    TransferPriority(String displayName) {
        this.displayName = displayName;
    }

    public boolean requiresImmedateAttention() {
        return this == URGENT || this == CRITICAL;
    }

    public int getPriorityLevel() {
        return switch (this) {
            case LOW -> 1;
            case NORMAL -> 2;
            case HIGH -> 3;
            case URGENT -> 4;
            case CRITICAL -> 5;
        };
    }
}

