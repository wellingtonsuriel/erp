package com.pos_onlineshop.hybrid.enums;

/**
 * Fine-grained backend authorities for accounting/ERP endpoints, distinct from the
 * POS-terminal-scoped Permission/CashierPermission model (which governs what a cashier can
 * do at the till, not what a back-office UserAccount can do against the API). Granted per
 * UserAccount via UserAccountPermission and exposed as plain (unprefixed) GrantedAuthority
 * names, checked with @PreAuthorize("hasAuthority('GL_POST')") etc. - see
 * UserAccountService.loadUserByUsername for how these are added to the authenticated
 * principal alongside ROLE_* authorities.
 */
public enum AccountingPermission {
    GL_VIEW, GL_POST, GL_REVERSE, GL_MANUAL_JOURNAL, GL_APPROVE, GL_ADMIN,
    AP_VIEW, AP_CREATE, AP_APPROVE, AP_PAY,
    AR_VIEW, AR_CREATE, AR_RECEIVE,
    INVENTORY_VIEW, INVENTORY_ADJUST, INVENTORY_TRANSFER,
    PROCUREMENT_VIEW, PROCUREMENT_CREATE, PROCUREMENT_APPROVE,
    REPORT_VIEW, PERIOD_CLOSE, PERIOD_REOPEN,
    AUDIT_VIEW, USER_ADMIN
}
