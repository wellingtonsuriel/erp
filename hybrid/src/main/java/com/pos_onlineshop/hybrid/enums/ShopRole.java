package com.pos_onlineshop.hybrid.enums;

/**
 * Which shop dimension on a FinancialEvent a PostingRuleLine's costCenter comes from.
 * Every event type except INVENTORY_TRANSFER only ever has one shop (SOURCE, i.e.
 * FinancialEvent.shop); a transfer is the one case with two shops in play at once.
 */
public enum ShopRole {
    SOURCE,
    DESTINATION
}
