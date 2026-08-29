# Foreign Exchange and Hyperinflation Accounting

## Base-amount accounting

`JournalLine.baseAmount = amount * exchangeRate` is the single authoritative accounting
value - `JournalValidator`'s balance check, every report, and every reconciliation sum
`baseAmount`, never the raw transaction-currency `debitAmount`/`creditAmount`. This is a
strict widening over summing raw amounts: a same-currency entry balances identically either
way, but a genuinely multi-currency entry can only balance correctly in base-currency terms.

Any service posting a line whose currency might differ from the base currency must look up
the real rate via `CurrencyService.getExchangeRate(currency, baseCurrency)` - never assume
1:1. (This session found and fixed several services that had gotten this wrong by copying an
established-but-incorrect pattern; see the git history around "use real exchange rates
instead of hardcoded 1:1" for the specifics and which services were confirmed already
correct.)

## Realized FX (settlement)

`CustomerReceiptService`/`SupplierPaymentService` post three lines when a foreign-currency
invoice is settled: the cash/bank leg at the settlement rate, the AR/AP leg at the invoice's
*currently-carried* rate (`CustomerInvoice.exchangeRate`/`SupplierInvoice.exchangeRate`), and
a plug to 5900 FX Gain/Loss for the difference. For AR (an asset), worth-more-now is a gain
(debit AR / credit FX); for AP (a liability), worth-more-now is a loss (credit AP / debit
FX) - the mirror image of AR, a common source of sign-direction bugs.

## Unrealized FX (revaluation)

`FxRevaluationService.revalueOpenBalances()` sweeps every open foreign-currency AR/AP
invoice, diffs its outstanding balance against its *currently-carried* rate, posts the
incremental movement to 1100/2100 vs 5900, then advances the invoice's exchange rate to the
new rate. Treating the invoice's rate as a rolling "currently carried at" value (rather than
fixed at the original booking rate) is what avoids double-counting the same rate movement
across periods, and is exactly why realized FX at eventual settlement composes correctly
with this with no special-casing.

## IAS 29 (hyperinflation) restatement

`GeneralPriceIndexService` records published general price index readings (e.g. monthly
CPI) and computes the conversion factor between any two dates:
`indexAsAt(toDate) / indexAsAt(fromDate)`. `Ias29RestatementService` is what actually applies
that factor - currently scoped to Fixed Assets, the one non-monetary category with real
acquisition-date data (every `FixedAsset` has `acquisitionDate`, every depreciation charge
has its own `periodDate`). Gross cost is restated using the factor from acquisition date;
each depreciation charge is restated using the factor from *its own* period date, then
summed - collapsing this into a single acquisition-date factor applied to net book value
would overstate the restated accumulated depreciation. The net movement posts to 3910 IAS 29
Restatement Reserve. `Account.monetary` classifies which accounts are subject to
restatement at all (monetary items - cash, AR, AP - never are).

Restatement only runs during period close when `GeneralPriceIndexService.hasAnyReadings()`
is true - a business that has never recorded a price index reading (the default, typical
case) is never forced through IAS 29 machinery it doesn't need.

## Known limitations

- Inventory and Equity are also non-monetary under IAS 29 but have no acquisition-date
  tracking at the lot/movement level - restating them would mean guessing a date, which this
  system refuses to do.
- Net monetary gain/loss (the P&L consequence of holding a net monetary position through a
  hyperinflationary period) is not computed - it requires tracking net monetary position
  through the period, not just its endpoints.
- FX revaluation covers AR/AP only - Cash/Bank balances denominated in a foreign currency are
  not revalued (a `BankAccount` carries a currency, but there is no revaluation sweep for it
  yet).
