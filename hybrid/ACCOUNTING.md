# Accounting / General Ledger

## Overview

This system runs on a real double-entry general ledger, not a set of ad-hoc revenue/expense
counters. Every financial event in the business - a POS sale, a supplier invoice, a payroll
run, a fixed asset disposal - eventually becomes a balanced `JournalEntry` made up of
`JournalLine` rows, each hitting exactly one `Account` from the chart of accounts on one side
(debit or credit).

Two ways an event becomes a journal entry:

- **`PostingRule`-driven** (`GLPostingService.post(FinancialEvent)`): a subledger module
  (POS, inventory receipts, customer/supplier settlement) describes *what happened* - an
  event type plus gross/net/tax/cost amounts - and a seeded `PostingRule` decides which
  accounts each slice lands on. The subledger never names an account directly.
- **Manual line specs** (`GLPostingService.postManual(...)`): used when the accounts
  genuinely vary per invocation and can't be expressed as a fixed rule - manual journals,
  payroll, fixed asset acquisition/depreciation/disposal, FX revaluation, IAS 29
  restatement, period-close sweeps. The caller builds a list of `ManualLineSpec` and the
  entry is validated and posted the same way either path produces it.

Every posting is idempotent by a caller-supplied key (`JournalEntry.idempotencyKey`) - a
retried call replays the existing entry rather than double-posting. Reversals
(`GLPostingService.reverse(...)`) flip every line of a POSTED entry rather than editing or
deleting it; nothing in this system ever rewrites a posted journal.

## Base currency and FX

`JournalLine.baseAmount = amount * exchangeRate` is the authoritative accounting value -
every report, every balance validation, every reconciliation reads `baseAmount`, never the
raw transaction-currency `debitAmount`/`creditAmount`. This matters because a genuinely
multi-currency entry can look unbalanced in raw amounts while balancing correctly in base
currency, and vice versa. Every service that posts a foreign-currency line looks up the real
rate via `CurrencyService.getExchangeRate()` rather than assuming 1:1 - see `FX.md`.

## Chart of accounts

Seeded idempotently at startup by `GLAccountSeedService` (safe to run every boot - matches
by code, never duplicates). Key ranges:

| Range | Type | Examples |
|---|---|---|
| 1000s | Assets | 1010 Cash, 1020 Mobile Money/Card Clearing, 1030 Bank, 1100 AR, 1200 Inventory, 1400 VAT Input, 1500 Fixed Assets, 1590 Accumulated Depreciation |
| 2000s | Liabilities | 2100 AP, 2200 VAT Output, 2300 Customer Deposits & Loyalty, 2400 Payroll Payable, 2410 Payroll Deductions Payable |
| 3000s | Equity | 3000 Retained Earnings, 3900 Opening Balance Equity, 3910 IAS 29 Restatement Reserve |
| 4000s | Revenue | 4000 POS Sales, 4010 Online Sales, 4020 Credit/Wholesale, 4900 Sales Returns & Allowances |
| 5000s | Expenses | 5000 COGS, 5100 Inventory Write-off, 5200 Salaries, 5300 Operating Expenses, 5400 Depreciation, 5500 Bank Charges, 5600 Loyalty Program Expense, 5900 FX Gain/Loss, 5950 Gain/Loss on Disposal |

`Account.controlAccount` marks a subledger-backed balance (AR, AP, Inventory, Fixed Assets,
Payroll Payable/Deductions) that only a `SYSTEM`-sourced posting may touch directly - a
`MANUAL`-sourced entry is blocked from posting to one, so a human can't accidentally (or
deliberately) misstate a balance a subledger is supposed to own. `Account.monetary`
classifies IAS 29 restatement eligibility (see `FX.md`).

## Accounting periods and close

`AccountingPeriod` is calendar-month, auto-opened on first use. `AccountingPeriodService.
closePeriod()` runs the full period-end sequence in one transaction:

1. Reverse every accrual due for reversal (`AccrualService`).
2. Run monthly depreciation (`AssetDepreciationService`).
3. Revalue open foreign-currency AR/AP to period-end rates (`FxRevaluationService`).
4. Restate fixed assets to period-end price levels, only if a price index has ever been
   recorded (`Ias29RestatementService`).
5. Validate the trial balance is actually balanced.
6. Sweep every REVENUE/EXPENSE account's net movement to Retained Earnings.
7. Mark the period CLOSED.

Every step is idempotent, so reopening and reclosing a period safely re-runs all of it
without double-posting. Closing a period is always a deliberate API call - nothing in this
system closes a period on a timer (see `ScheduledJobsService`'s own class comment for why).

## Reporting

All reports read exclusively from `JournalEntry`/`JournalLine` - Trial Balance, Profit &
Loss, Balance Sheet, Cash Flow, VAT Return, AR/AP Aging, Customer/Supplier Statements, and
the GL account-ledger drill-down (`GeneralLedgerService` - one account's individual postings
with a running balance). `ControlAccountReconciliationService` compares each control
account's GL balance against its subledger and persists a history of runs with
resolution tracking for any variance found. A separate `LegacyGlReconciliationService`
diagnostic compares the GL against a pre-GL `AccountancyEntry` ledger that is still written
in parallel today, gated behind the `legacy-gl.dual-write-enabled` switch - see
`AccountancyService`'s class comment for the cutover plan.

## Governance

- `ManualJournalService`: maker-checker (DRAFT → SUBMITTED → APPROVED → POSTED, or → REJECTED)
  for manual journals specifically - the preparer can never also be the approver.
- `WorkflowService`/`ApprovalRequest`: a generic, standalone approval primitive for any
  NEW flow that wants gating without its own status machine - deliberately not a
  replacement for ManualJournal's or Expense's own working maker-checker logic.
- `AuditLogService`: a centralized, immutable audit trail, wired into manual journal
  approve/reject/post and period close/reopen today - see its class comment for exactly
  what's covered and what isn't yet.

## Further reading

- `AP.md` - accounts payable (supplier invoices, payments, debit notes, statements)
- `AR.md` - accounts receivable (customer invoices, receipts, credit notes, statements)
- `FIXED_ASSETS.md` - registration, depreciation, disposal
- `FX.md` - base-amount accounting, realized/unrealized FX, IAS 29 restatement
- `PAYROLL.md` - payroll runs, deductions, payslips
