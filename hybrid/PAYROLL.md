# Payroll

## Entities

- **`Employee`**: a minimal HR record, deliberately separate from `UserAccount` (login
  credentials/roles are an authentication concern; being paid is an HR concern - the two
  don't always coincide). `baseSalary`/`salaryCurrency` are null for an employee not on
  payroll (e.g. one only ever reimbursed for expenses via `Expense`).
  `isPayrollEligible()` = active and a positive `baseSalary`.
- **`DeductionType`**: extensible, admin-configured percentage-of-gross or fixed-amount
  deduction - deliberately **not** a hardcoded Zimbabwe statutory calculation (PAYE/NSSA/AIDS
  levy). This system has no authoritative statutory rules to encode, and guessing them would
  be worse than not implementing them. Every ACTIVE deduction applies to every
  payroll-eligible employee in a run.
- **`PayrollRun`**: no DRAFT state - `PayrollService.processPayroll()` computes every payslip
  and posts the accrual atomically in one action, so a run is PROCESSED the instant it
  exists. Two-posting mechanics:
  - **Accrual** (at processing time): Dr 5200 Salary and Wages Expense = Cr 2400 Payroll
    Payable (net) + Cr 2410 Payroll Deductions Payable (total deductions).
  - **Payment** (a separate, later action - `payRun()`): Dr 2400 Payroll Payable / Cr 1010
    Cash or 1030 Bank for the net pay only. Guarded by `canBePaid()` so a run can never be
    paid twice.
- **`Payslip`**: an immutable per-employee snapshot - `grossPay` is copied from
  `Employee.baseSalary` at run time and never re-read live, so a later salary change never
  rewrites payroll history.

## Known limitations

- Deductions are global, not per-employee - there is no per-employee opt-out/override.
- A payroll run has a single currency, and every eligible employee's `salaryCurrency` must
  match it - true multi-currency payroll within one run is not supported.
- No employer-cost-side postings (e.g. employer NSSA contribution) - only the employee-side
  accrual is modeled.
