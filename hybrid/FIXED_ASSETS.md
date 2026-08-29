# Fixed Assets

## Lifecycle

`FixedAsset`: acquisition cost, useful life, and residual value are fixed at registration -
correcting a mistake is a new transaction, not an edit, matching every other posted-document
entity in this system. `accumulatedDepreciation` is the one mutable operational field;
`netBookValue`/`depreciableBase`/`isFullyDepreciated` are always computed from it, never
stored, so they can never drift out of sync.

- **Registration** (`FixedAssetService`): posts Dr 1500 Fixed Assets / Cr 2100 Accounts
  Payable atomically with the asset record - always on-account (see `AP.md`'s known
  limitations).
- **Depreciation** (`AssetDepreciationService`): straight-line only (`DepreciationMethod`
  exists as an extensibility point for future methods). `depreciableBase / usefulLifeMonths`
  per month, capped so the asset is never depreciated below residual value. Idempotent two
  ways: a DB-unique `(asset_id, period_date)` constraint, and an explicit pre-check before
  computing anything - a retried run for an already-processed period is a silent no-op.
  Posts Dr 5400 Depreciation Expense / Cr 1590 Accumulated Depreciation.
- **Disposal** (`AssetDisposalService`): clears 1500/1590 for the asset, records any
  proceeds, plugs the gain/loss to 5950.

## IAS 29 restatement

`Ias29RestatementService` restates gross cost and accumulated depreciation to current price
levels using `GeneralPriceIndexService`'s conversion factor - see `FX.md` for the full
mechanism (gross cost and accumulated depreciation are restated *separately*, since each
depreciation charge uses the price-index factor from its own period date, not the asset's
acquisition date).

## Known limitations

- Acquisition is always on-account (Dr 1500 / Cr 2100) - no cash/bank-funded acquisition
  path exists yet.
- Only straight-line depreciation is implemented; reducing-balance and other methods are not.
- IAS 29 restatement covers Fixed Assets only - Inventory and Equity are also non-monetary
  under IAS 29 but this system has no acquisition-date tracking at the lot/movement level
  for either yet.
