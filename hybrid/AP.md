# Accounts Payable

## Entities

- **`SupplierInvoice`**: DRAFT → POSTED → PARTIALLY_PAID → PAID (or VOIDED). Posting debits
  the relevant expense/inventory account(s) and credits 2100 Accounts Payable. A
  `SupplierInvoice` linked to a `PurchaseOrder` posts through the PO-receipt flow; a
  standalone invoice (no PO - a service/expense bill) posts via the seeded
  `PURCHASE_INVOICE` FinancialEvent/PostingRule instead - see `SupplierInvoiceService`'s
  class comment for exactly which case applies when.
- **`SupplierPayment`**: settles an invoice, in full or in part. Posts Dr 2100 Accounts
  Payable (at the invoice's currently-carried exchange rate) / Cr 1010 Cash or 1030 Bank (at
  the settlement rate), plugging any FX difference to 5900 FX Gain/Loss - see `FX.md` for
  the realized-FX mechanics.
- **`SupplierDebitNote`**: reduces an invoice's outstanding balance without cash changing
  hands (a return, pricing correction, or credit) - always standalone against a specific
  invoice, no inventory/COGS side to reverse (the original invoice never posted one either
  for a non-PO invoice).

## Bank/cash accounts

`Expense`, `SupplierPaymentService`, and the cash/bank subledger (`BankAccount`,
`CashBankTransfer`, `BankCharge` - see below) all settle against 1010 Cash on Hand or 1030
Bank depending on payment method. `BankAccount` is a *named* subledger row (e.g. "CBZ Main
Account") that rolls up to one of the flat control accounts (1010/1020/1030) - multiple
named accounts can share one control code, the same many-subledger-rows-to-one-control-
account pattern as everything else in this system. `CashBankTransfer` moves money between
two `BankAccount`s (deposits, withdrawals, inter-account transfers, mobile-money
settlement are all the same mechanism: Dr the destination / Cr the source). `BankCharge`
posts a bank-deducted fee (Dr 5500 Bank Charges / Cr the account).

## Reporting

- **AP Aging** (`ApAgingService`): outstanding POSTED/PARTIALLY_PAID invoices bucketed by
  days overdue.
- **Supplier Statement** (`SupplierStatementService`): opening balance + running balance of
  every real (POSTED) transaction in a date range - invoices debit, payments/debit notes
  credit, matching the same sign convention `applyPayment` uses internally.
- **Control account reconciliation**: 2100's GL balance is compared against the sum of every
  open `SupplierInvoice.outstandingAmount` - see `ACCOUNTING.md`.

## Known limitations

- Fixed-asset acquisition is always posted on-account (Dr 1500 / Cr 2100) - there is no
  cash/bank-funded acquisition path yet.
- Expense is deliberately scoped to cash/bank-settled spend only; on-account supplier spend
  belongs to `SupplierInvoice`, not `Expense` - see `Expense`'s own class comment for the
  boundary.
