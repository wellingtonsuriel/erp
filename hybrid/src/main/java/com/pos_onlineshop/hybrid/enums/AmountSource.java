package com.pos_onlineshop.hybrid.enums;

/**
 * Which slice of a FinancialEvent's amounts a given PostingRuleLine pulls its value from.
 * A line whose selected amount is null or zero is skipped when the rule is resolved,
 * which is how e.g. a COGS/Inventory pair is omitted for an event with no known cost.
 */
public enum AmountSource {
    GROSS,
    NET,
    TAX,
    COST
}
