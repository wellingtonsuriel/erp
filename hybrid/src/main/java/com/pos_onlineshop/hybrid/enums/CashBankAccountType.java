package com.pos_onlineshop.hybrid.enums;

/** What a BankAccount subledger row represents - drives which control GL account
 * (1010/1020/1030) it defaults to. See BankAccount.defaultGlAccountCode(). */
public enum CashBankAccountType {
    CASH,
    BANK,
    MOBILE_MONEY
}
