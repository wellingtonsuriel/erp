package com.pos_onlineshop.hybrid.enums;

public enum Permission {
    // Basic POS operations
    PROCESS_SALE,
    PROCESS_RETURN,
    APPLY_DISCOUNT,

    // Cash management
    OPEN_CASH_DRAWER,
    PERFORM_CASH_COUNT,
    ADJUST_CASH,

    // Inventory operations
    VIEW_INVENTORY,
    TRANSFER_INVENTORY,
    ADJUST_INVENTORY,

    // Administrative
    VIEW_REPORTS,
    MANAGE_CASHIERS,
    OVERRIDE_PRICE,
    VOID_TRANSACTION,

    // System
    ACCESS_BACK_OFFICE,
    MODIFY_SETTINGS
}
