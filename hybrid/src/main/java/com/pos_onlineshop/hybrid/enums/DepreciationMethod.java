package com.pos_onlineshop.hybrid.enums;

/** Only STRAIGHT_LINE is implemented (see FixedAssetService.calculateMonthlyDepreciation) -
 * the enum exists so a future method (e.g. REDUCING_BALANCE) is a new switch branch, not a
 * schema change. */
public enum DepreciationMethod {
    STRAIGHT_LINE
}
