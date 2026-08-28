package com.pos_onlineshop.hybrid.enums;

/**
 * Business events the GL Posting Engine knows how to turn into a balanced journal entry.
 * Only POS_CASH_SALE, POS_NON_CASH_SALE and SALE_REFUND are emitted anywhere in the
 * codebase today (see POSService). The remaining values have seeded PostingRules ready
 * to receive events once inventory/order/session integration is implemented (pending work
 * documented in the GL implementation summary).
 */
public enum FinancialEventType {
    POS_CASH_SALE,
    POS_NON_CASH_SALE,
    ONLINE_ORDER_PAID,
    ONLINE_ORDER_UNPAID,
    SALE_REFUND,
    STOCK_RECEIPT,
    INVENTORY_TRANSFER,
    DAMAGED_STOCK,
    SESSION_CASH_SHORT,
    SESSION_CASH_OVER,
    LOYALTY_REDEMPTION,
    FX_REVALUATION,
    MANUAL_ENTRY
}
