# Accounts Receivable

## Entities

- **`CustomerInvoice`**: a standalone credit-sale invoice, independent of the POS/online
  `Order` pipeline (which has no on-account/unpaid concept of its own today). Posts Dr 1100
  Accounts Receivable / Cr 4020 Sales Revenue - Credit/Wholesale / Cr 2200 VAT Output.
- **`CustomerReceipt`**: settles an invoice, in full or in part. Posts Dr 1010 Cash or 1030
  Bank (at the settlement rate) / Cr 1100 Accounts Receivable (at the invoice's
  currently-carried rate), plugging any FX difference to 5900 FX Gain/Loss - see `FX.md`.
- **`CustomerCreditNote`**: reduces an invoice's outstanding balance without cash changing
  hands - always standalone against a specific invoice, no inventory/COGS side (the
  original invoice never posted one either).
- **`SalesReturn`**: the order-linked case `CustomerCreditNote` explicitly is not - reverses
  a completed POS/online sale's actual revenue, tax, COGS, and inventory effect for a full
  or partial return, at the price/cost the customer was actually charged/the business
  actually paid, never at today's prices. Validates against remaining returnable quantity
  per order line across every prior return against that line.
- **`LoyaltyAccount`/`LoyaltyTransaction`**: a monetary liability ledger against 2300
  Customer Deposits & Loyalty Liability, distinct from the legacy `Customers.loyaltyPoints`
  gamification counter. EARNED/EXPIRED/REVERSED post against 5600 Loyalty Program Expense;
  REDEEMED posts as if points were cash (Dr 2300 / Cr 4000). Every movement is rejected past
  `availableBalance` - a customer can never redeem, let expire, or have reversed more than
  they have.

## Reporting

- **AR Aging** (`ArAgingService`): outstanding POSTED/PARTIALLY_PAID invoices bucketed by
  days overdue.
- **Customer Statement** (`CustomerStatementService`): opening balance + running balance of
  every real (POSTED) transaction in a date range - invoices debit,
  receipts/credit-notes credit.
- **Control account reconciliation**: 1100's GL balance is compared against the sum of every
  open `CustomerInvoice.outstandingAmount` - see `ACCOUNTING.md`.

## Known limitations

- `SalesReturn` only supports orders whose gross side posted to 1010/1020 (POS cash/non-cash,
  or online-paid) - there is no `ONLINE_ORDER_UNPAID` (on-account) GL event yet to mirror.
- Loyalty earn/redeem is not yet automatically triggered by POS sales or returns - a caller
  (a future POS UI action) invokes `LoyaltyService` directly today.
