package com.pos_onlineshop.hybrid.enums;

import lombok.Getter;

@Getter
public enum TransferType {

    REPLENISHMENT("Stock Replenishment"),
    REBALANCING("Inventory Rebalancing"),
    EMERGENCY("Emergency Transfer"),
    RETURN("Product Return"),
    EXPIRED("Expired Product Transfer"),
    DAMAGED("Damaged Product Transfer"),
    SEASONAL("Seasonal Transfer"),
    PROMOTION("Promotional Transfer");

    private final String displayName;

    TransferType(String displayName) {
        this.displayName = displayName;
    }

    public boolean isUrgent() {
        return this == EMERGENCY;
    }
}
