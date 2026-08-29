package com.pos_onlineshop.hybrid.enums;

/**
 * What kind of quantity change an InventoryMovement records. Purely a quantity-side
 * classification - whether/how it has a monetary counterpart in the GL is a separate concern
 * decided by the caller (see e.g. RESERVATION never posting to the GL at all).
 */
public enum InventoryMovementType {
    RECEIPT,
    SALE,
    SALE_RETURN,
    TRANSFER_OUT,
    TRANSFER_IN,
    DAMAGE,
    ADJUSTMENT_IN,
    ADJUSTMENT_OUT,
    RESERVATION,
    RESERVATION_RELEASE
}
