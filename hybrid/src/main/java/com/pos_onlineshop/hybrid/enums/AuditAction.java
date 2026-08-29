package com.pos_onlineshop.hybrid.enums;

/** What happened to the audited entity - see AuditLogService. Deliberately coarse (not one
 * value per module) so every caller across the codebase can express its event without
 * needing a new enum value first. */
public enum AuditAction {
    CREATE,
    UPDATE,
    DELETE,
    APPROVE,
    REJECT,
    POST,
    REVERSE,
    PRICE_CHANGE,
    INVENTORY_ADJUSTMENT,
    PERIOD_CLOSE,
    PERIOD_REOPEN,
    PERMISSION_CHANGE,
    OTHER
}
