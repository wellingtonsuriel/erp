# Inventory Valuation (FIFO Cost Layers)

## Three distinct authorities, not one

- **Quantity** - `InventoryTotal`. The single live physical stock counter for both POS and
  online checkout (see `ShopInventoryService`'s class comment). Unchanged by this work.
- **Valuation** - `ShopInventory` rows, now real FIFO cost layers, read/written exclusively
  through `InventoryValuationService`. Each row is one stock receipt: `quantity` (what was
  received - immutable) and `remainingQuantity` (how much of it hasn't been consumed yet -
  the only mutable field). `InventoryTotal.totalstock` for a (shop, product) pair should equal
  the sum of its lots' `remainingQuantity`; see "Historical backfill" for how a pre-existing
  divergence is handled rather than assumed away.
- **Money** - the GL, via `GLPostingService`. Never computes a cost itself; every COGS/
  Inventory posting receives an already-computed `costAmount` from `InventoryValuationService`.

These three must stay reconcilable, never conflated - `ControlAccountReconciliationService`
already compares (2) against 1200 Inventory Asset's GL balance for exactly this reason.

## FIFO, not "latest lot"

Before this change, every cost lookup (COGS at sale, inventory valuation for the balance
sheet, damage write-off) used *the most recently received lot's price applied to the entire
on-hand quantity* - correct only when a single lot exists, and silently wrong the moment two
receipts at different prices coexist. `InventoryValuationService` is now the only place that
computes a cost, and does so by real FIFO consumption:

```
Receipt A: 100 @ $10
Receipt B:  50 @ $12
Sell 120 units:
  100 @ $10 = $1,000   (lot A fully consumed)
   20 @ $12 =   $240   (lot B partially consumed, 30 remain)
  COGS = $1,240
  Remaining inventory = 30 @ $12 = $360
```

`ShopInventoryService.calculateTotalInventoryValue()` is kept as a thin backward-compatible
wrapper around `InventoryValuationService.getTotalInventoryValue()`.

## Never guess a cost

If a quantity being sold/transferred/written off exceeds what recorded cost layers can
cover, the uncovered portion contributes **zero** to the cost - never a guessed or
selling-price-derived figure. The caller (POSService, OrderService) checks
`CostResult.fullyCosted`: when false, the whole COGS/Inventory GL pair is omitted rather than
posting a partial, understated one (the same pattern `STOCK_RECEIPT` already used for a
missing tax split). `InventoryValuationService.getInventoryValueDetailed` reports this the
same way for valuation reads: `unvaluedQuantity` is a real, inspectable field, never folded
silently into a $0 line.

## Where COGS is now posted

- **POS sale** (`POSService.processQuickSale`): FIFO cost is computed immediately after the
  order is saved, in the same journal entry as revenue/tax - unchanged in timing, only the
  cost source changed (real FIFO instead of latest-lot).
- **Online order**: revenue is recognized at checkout (`OrderService.postOnlineOrderToGeneralLedger`),
  while the stock is only *reserved*, not yet physically committed - so there is nothing to
  cost yet (`costAmount` stays null there, as it always has). COGS posts separately, in its
  own journal entry, the moment the reservation is actually committed to a physical stock
  reduction (`OrderService.postOnlineOrderCogsToGeneralLedger`, called from
  `updateOrderStatus`'s CONFIRMED branch) - a reservation alone must never touch the GL, and
  now the online channel finally recognizes COGS at all, closing a gap that previously left
  `costAmount` permanently null for every online sale regardless of whether a real cost layer
  existed.
- **Sales return** (`SalesReturnService`): restores a *new* cost layer at the unit cost the
  goods actually left at (`orderLine.getUnitCost()`), via `InventoryValuationService.restoreCostLayer`
  - never at selling price. Skipped, as before, when the original sale's cost was never known.

## Historical backfill

`ShopInventory.remainingQuantity` is nullable with **no** default - not even 0 - specifically
so a pre-existing row (from before FIFO cost layers existed) is unambiguously distinguishable
from a lot genuinely fully consumed to zero. `InventoryValuationService.backfillLayersIfNeeded`
runs lazily, once per (shop, product) pair, before any read or consumption:

1. Every lot with `remainingQuantity == null` is initialized to its full `quantity` (assume
   nothing consumed yet).
2. If the lots now sum to *more* than `InventoryTotal.totalstock`, the excess is depleted from
   the **oldest** lots first - the only defensible assumption with no record of actual
   historical depletion order, and the same FIFO convention enforced going forward.
3. If the lots sum to *less* than `totalstock`, the shortfall is left genuinely unlotted
   (valued at $0, surfaced via `ValuationResult.unvaluedQuantity`) rather than inventing a
   layer - this happens when stock was added via `ShopInventoryService.addStock` directly,
   without ever creating a `ShopInventory` row.

Every step is logged at WARN so a real discrepancy is visible in the application log, not
silently absorbed.

## InventoryMovement - the quantity audit trail

A new, append-only `inventory_movements` table records every quantity change (`RECEIPT`,
`SALE`, `SALE_RETURN`, `TRANSFER_OUT`, `TRANSFER_IN`, `DAMAGE`, `ADJUSTMENT_IN`,
`ADJUSTMENT_OUT`, `RESERVATION`, `RESERVATION_RELEASE`) - separate from both `ShopInventory`
(cost layers) and `JournalEntry` (money), per the standing rule that quantity, valuation, and
money must stay distinguishable. Currently written for: receipts (`ShopInventoryService.createShopInventory`),
reservations/releases (`ShopInventoryService.reserveStock`/`releaseReservation`), FIFO
consumption (`InventoryValuationService.consumeCostLayers`, covers POS/online COGS and
inter-shop transfer-out), and cost-layer restoration (`InventoryValuationService.restoreCostLayer`,
covers sales returns, transfer-in, and restoring a cancelled in-transit transfer's source layer).

## Inter-shop transfers: real FIFO cost, not a manually-carried estimate

`InventoryTransferService.shipTransfer` now consumes the *source* shop's real FIFO cost
layers for the shipped quantity at the moment of shipping (`TRANSFER_OUT`) - the point at
which the units are genuinely, permanently leaving that shop - and overwrites
`InventoryTransferItem.unitCost` with the real weighted cost, superseding whatever estimate
was entered when the item was added to the transfer. `receiveTransfer` then creates the
*destination* shop's cost layer (`TRANSFER_IN`) at that same real cost for the received
portion, and still uses `unitCost` unmodified for the pre-existing damaged-portion write-off
math. `cancelTransfer` restores the source shop's layer (`ADJUSTMENT_IN`) if an `IN_TRANSIT`
transfer is cancelled before receipt, since the ship-time consumption must be undone along
with the `InventoryTotal` reversal that already happened. Every step is skipped rather than
guessed when a shipment's cost layers don't fully cover it (never null'd to a fabricated
value) - see `InventoryTransferServiceTest`.

## Reporting API and reconciliation

`GET /api/inventory/balances|valuation|movements` (`InventoryReportController`) and
`GET /api/reports/inventory-reconciliation` (`GLReportController`, backed by
`InventoryReportService`) expose the three dimensions - quantity, FIFO valuation, and the GL's
1200 Inventory Asset balance (via `ControlAccountReconciliationService.getInventoryAssetGlBalance`)
- as separate figures, never merged into one number. The reconciliation report states a
variance rather than hiding one when inventory value and the GL balance disagree. The erp-frontend
app's `/admin/accounting/inventory-reconciliation` page renders this: on-hand/reserved/available,
inventory value vs. GL inventory asset vs. variance, and a Reconciled/variance badge, plus a
drill-down into the three underlying reports.

## Automated verification of the accounting invariants

`InventoryAccountingAcceptanceTest` wires real `ShopInventoryService` +
`InventoryValuationService` instances together (hand-rolled in-memory fakes for the
repositories) and transcribes the architecture prompt's worked acceptance example end to end:
receive 100 @ $10 -> sell 60 -> reserve 10 online -> fulfill the reservation, asserting on-hand/
reserved/available quantity, FIFO valuation, and COGS at each step, and that a reservation alone
never posts to the GL.

`InventoryConcurrencySafetyTest` is the one guarantee a mocked-repository test cannot prove: it
uses a real H2 database (`@DataJpaTest`, since MySQL is unreachable in this development
environment) and fires ten threads at `ShopInventoryService.reserveStock` for the same
`InventoryTotal` row simultaneously, asserting `reservedStock` never exceeds `totalstock` - i.e.
that the `@Lock(PESSIMISTIC_WRITE)` read-check-write sequence is actually race-free under real
concurrent transactions, not just correct when called from one thread.

## Idempotency on retry

`InventoryValuationService.consumeCostLayers` and `restoreCostLayer` check for an existing
`InventoryMovement`/`ShopInventory` record keyed on `(shop, product, movementType, reference)` (or
`sourceReference`) before mutating, and return the already-recorded outcome on a match instead of
consuming or restoring a layer a second time - the same replay-returns-the-existing-result pattern
`GLPostingService.post` already uses for the GL side. Every call site now scopes `reference` to one
financially-significant event (one order line, one transfer item, one return line) rather than
sharing a reference across an entire order/transfer/return, since a shared reference would make
legitimate repeat lines of the same order/transfer wrongly dedupe against each other. This closes a
real gap: previously, a retried `postOnlineOrderCogsToGeneralLedger` (e.g. a repeated CONFIRMED
transition) would double-consume FIFO layers even though `GLPostingService`'s own idempotency key
already blocked the resulting journal entry from posting twice - an inventory/GL divergence that
would have gone undetected until a reconciliation run caught it.

## Known limitations (disclosed, not hidden)

- **Multi-currency line-level FX** (§16 of the inventory/accounting architecture prompt - a
  proper transaction-amount/base-amount/exchange-rate split on every JournalLine) is
  unaddressed; `JournalLine.baseAmount` remains the only authoritative monetary figure per the
  existing FX model documented in `FX.md`. Deliberately deferred - the prompt is explicit that
  FX must not be bolted onto an unproven foundation.
- **No client-supplied idempotency key for whole-request retries.** The idempotency guards
  above stop *layers* from being double-consumed/restored, but nothing yet stops a client from
  resubmitting an entire POS sale or online order as a brand-new `Order` (a fresh ID each time,
  so the reference-based guard can't recognize it as the same request). Closing that gap needs
  a client-supplied idempotency key threaded through order creation itself, which is a separate,
  larger change to the API contract, not an inventory/valuation concern.
