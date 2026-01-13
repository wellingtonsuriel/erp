package com.pos_onlineshop.hybrid.enums;

/**
 * TaxCalculationType enum representing different methods of calculating taxes.
 */
public enum TaxCalculationType {
    FIXED,        // Fixed amount per item (e.g., $2 per item)
    PERCENTAGE,   // Percentage of the price (e.g., 15%)
    TIERED        // Tiered/bracketed tax calculation
}
