package com.pos_onlineshop.hybrid.enums;

/** Every value moves LoyaltyAccount.availableBalance and posts against 2300 Customer
 * Deposits & Loyalty Liability - see LoyaltyService. EARNED increases the balance (a new
 * liability to the customer); REDEEMED, EXPIRED, and REVERSED all decrease it (the
 * liability is settled, forfeited, or clawed back) and are rejected past the available
 * balance - a customer can never redeem, let expire, or have reversed more than they
 * actually have. */
public enum LoyaltyTransactionType {
    EARNED,
    REDEEMED,
    EXPIRED,
    REVERSED
}
