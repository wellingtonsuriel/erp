package com.pos_onlineshop.hybrid.enums;

// Price type enum
public enum PriceType {
//    REGULAR,      // Regular price
//    PROMOTIONAL,  // Promotional price
//    SALE,         // Sale price
//    WHOLESALE,    // Wholesale price
//    RETAIL,       // Retail price
//    BULK,         // Bulk purchase price
//    MEMBER,// Member-only price
SALE("SALE"),
    REGULAR("Regular Price"),
    PROMOTIONAL("Promotional Price"),
    CLEARANCE("Clearance Price"),
    BULK("Bulk Price"),
    MEMBER("Member Price"),
    WHOLESALE("Wholesale Price"),
    RETAIL("Retail Price"),
    ONLINE("Online Price"),
    SEASONAL("Seasonal Price"),
    FLASH_SALE("Flash Sale Price");

    private final String displayName;

    PriceType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}


