package com.pos_onlineshop.hybrid.enums;

/** No DRAFT state: PayrollService.processPayroll() computes every payslip and posts the
 * accrual atomically in one action (the same "one action, one transaction" pattern used
 * throughout this codebase), so a run is PROCESSED the moment it exists. */
public enum PayrollRunStatus {
    PROCESSED,
    PAID
}
