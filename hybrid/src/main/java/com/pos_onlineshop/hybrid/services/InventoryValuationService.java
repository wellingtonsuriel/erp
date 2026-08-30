package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.enums.InventoryMovementType;
import com.pos_onlineshop.hybrid.inventoryMovement.InventoryMovement;
import com.pos_onlineshop.hybrid.inventoryMovement.InventoryMovementRepository;
import com.pos_onlineshop.hybrid.inventoryTotal.InventoryTotal;
import com.pos_onlineshop.hybrid.inventoryTotal.InventoryTotalRepository;
import com.pos_onlineshop.hybrid.products.Product;
import com.pos_onlineshop.hybrid.shop.Shop;
import com.pos_onlineshop.hybrid.shopInventory.ShopInventory;
import com.pos_onlineshop.hybrid.shopInventory.ShopInventoryRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The single authoritative source for "how much is our inventory worth" and "what did these
 * units cost" - true FIFO cost-layer accounting, replacing the "value everything on hand at
 * the most recently received lot's price" approximation every caller used to compute
 * independently (POSService, ShopInventoryService.calculateTotalInventoryValue,
 * InventoryTransferService). That approximation is exact only when a single lot exists for a
 * pair; as soon as two receipts at different costs coexist it misstates both COGS and the
 * balance-sheet inventory figure. FIFO is correct in both cases and costs nothing extra once
 * ShopInventory carries a real remainingQuantity per lot.
 *
 * ShopInventory rows are the cost layers (see its class comment); InventoryTotal remains the
 * authoritative live quantity. This service is the only place layers are actually consumed
 * (decremented) or replenished (a new lot inserted) - every other service that needs a cost
 * or a valuation must call in here rather than querying ShopInventory/InventoryTotal directly
 * for that purpose.
 *
 * Cost is never guessed. A quantity this service cannot cover with a real cost layer (no lot
 * on record, or a pre-FIFO gap - see backfillLayersIfNeeded) contributes zero to the returned
 * total and is reported back explicitly via ValuationResult.unvaluedQuantity /
 * CostResult.fullyCosted, never silently folded into the total as if it were $0 by design.
 *
 * Historical data: ShopInventory.remainingQuantity is null on every row that existed before
 * this service did (see the field's own comment). backfillLayersIfNeeded is called before any
 * read or consumption of a pair's layers and initializes those null rows exactly once, using
 * the only defensible assumption available with no record of actual historical depletion
 * order: consumption happened oldest-lot-first, the same FIFO convention this service itself
 * enforces going forward. If a pair's lots even at full (unconsumed) quantity sum to less than
 * InventoryTotal.totalstock - meaning some on-hand stock was added via
 * ShopInventoryService.addStock without ever creating a lot - the shortfall is left genuinely
 * unlotted (valued at $0, reported as unvaluedQuantity) rather than fabricated into an
 * invented layer.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryValuationService {

    private final ShopInventoryRepository shopInventoryRepository;
    private final InventoryTotalRepository inventoryTotalRepository;
    private final InventoryMovementRepository inventoryMovementRepository;

    /** Result of costing a quantity for sale/transfer-out/damage/adjustment via FIFO layers. */
    @Data
    @Builder
    public static class CostResult {
        private BigDecimal totalCost;
        private int quantityCosted;
        private int quantityRequested;
        /** True only if every unit of quantityRequested was covered by a real cost layer. */
        private boolean fullyCosted;
    }

    /** Result of valuing on-hand stock for a (shop, product) pair (or an aggregate of pairs). */
    @Data
    @Builder
    public static class ValuationResult {
        private BigDecimal totalValue;
        private int valuedQuantity;
        /** On-hand quantity with no cost layer to price it - see the class comment. Always 0
         * once every pair has been backfilled and every receipt goes through a lot. */
        private int unvaluedQuantity;
    }

    // ------------------------------------------------------------------
    // Unit cost (front-of-queue, for display/estimation - not a consuming read)
    // ------------------------------------------------------------------

    /** The unit cost of the oldest lot still carrying remaining quantity - the price the next
     * unit sold will actually be costed at. Empty if no lot with remaining stock exists. */
    @Transactional
    public Optional<BigDecimal> getUnitCost(Shop shop, Product product) {
        backfillLayersIfNeeded(shop, product);
        return shopInventoryRepository
                .findAllByShopIdAndProductIdOrderByIdAsc(shop.getId(), product.getId())
                .stream()
                .filter(lot -> remaining(lot) > 0)
                .findFirst()
                .map(ShopInventory::getUnitPrice);
    }

    // ------------------------------------------------------------------
    // Valuation (read-only)
    // ------------------------------------------------------------------

    @Transactional
    public BigDecimal getInventoryValue(Shop shop, Product product) {
        return getInventoryValueDetailed(shop, product).getTotalValue();
    }

    @Transactional
    public ValuationResult getInventoryValueDetailed(Shop shop, Product product) {
        backfillLayersIfNeeded(shop, product);
        List<ShopInventory> lots = shopInventoryRepository
                .findAllByShopIdAndProductIdOrderByIdAsc(shop.getId(), product.getId());

        BigDecimal value = BigDecimal.ZERO;
        int valuedQty = 0;
        for (ShopInventory lot : lots) {
            int qty = remaining(lot);
            if (qty <= 0) {
                continue;
            }
            value = value.add(lot.getUnitPrice().multiply(BigDecimal.valueOf(qty)));
            valuedQty += qty;
        }

        int totalOnHand = inventoryTotalRepository.findByShopAndProduct(shop, product)
                .map(InventoryTotal::getTotalstock).orElse(0);
        int unvalued = Math.max(0, totalOnHand - valuedQty);
        if (unvalued > 0) {
            log.warn("Inventory value gap: shop {} product {} has {} on-hand units with no cost layer - "
                            + "valued at $0 for this portion rather than a guessed cost",
                    shop.getId(), product.getId(), unvalued);
        }

        return ValuationResult.builder()
                .totalValue(value)
                .valuedQuantity(valuedQty)
                .unvaluedQuantity(unvalued)
                .build();
    }

    @Transactional
    public BigDecimal getInventoryValue(Shop shop) {
        return inventoryTotalRepository.findByShopId(shop.getId()).stream()
                .filter(it -> it.getTotalstock() != null && it.getTotalstock() > 0)
                .map(it -> getInventoryValue(it.getShop(), it.getProduct()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** The authoritative "how much is our inventory worth" figure across every shop and
     * product - what ControlAccountReconciliationService compares against the 1200 GL
     * balance, and what ShopInventoryService.calculateTotalInventoryValue now delegates to. */
    @Transactional
    public BigDecimal getTotalInventoryValue() {
        return inventoryTotalRepository.findAllWithShopAndProduct().stream()
                .filter(it -> it.getTotalstock() != null && it.getTotalstock() > 0)
                .map(it -> getInventoryValue(it.getShop(), it.getProduct()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ------------------------------------------------------------------
    // Consumption (sale / transfer-out / damage / adjustment-out)
    // ------------------------------------------------------------------

    /** Costs a sale via real FIFO consumption of cost layers, decrementing them and recording
     * an audit InventoryMovement. Never guesses: if the requested quantity exceeds what the
     * recorded layers can cover, the uncovered portion is excluded from totalCost and
     * CostResult.fullyCosted is false - the caller decides what to do with a partially-known
     * cost (see POSService/OrderService, which omit the whole COGS/Inventory GL pair rather
     * than post a partial, understated one). */
    public CostResult getCostForSale(Shop shop, Product product, int quantity, String reference) {
        return consumeCostLayers(shop, product, quantity, InventoryMovementType.SALE, reference, LocalDate.now());
    }

    /** General-purpose FIFO consumption for any quantity-reducing movement that needs a real
     * cost (sale, transfer-out, damage, adjustment-out). Idempotent on (shop, product,
     * movementType, reference): every call site scopes reference to one financially-significant
     * event (one order line, one transfer item, one damage report - never shared across several
     * events), so a movement already recorded under that exact key means this exact consumption
     * already happened. A retry (e.g. a client resubmitting the same request) must not consume
     * the same layers twice - that would double-deplete stock no longer on the shelf and double
     * the resulting COGS, exactly the "duplicate postings on retry" failure this mirrors
     * GLPostingService.post's own idempotency check to prevent. */
    @Transactional
    public CostResult consumeCostLayers(Shop shop, Product product, int quantity,
                                         InventoryMovementType movementType, String reference,
                                         LocalDate transactionDate) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity to cost must be positive, received: " + quantity);
        }

        Optional<CostResult> replay = replayIfAlreadyRecorded(shop, product, quantity, movementType, reference);
        if (replay.isPresent()) {
            return replay.get();
        }

        backfillLayersIfNeeded(shop, product);
        List<ShopInventory> lots = shopInventoryRepository
                .findAllByShopIdAndProductIdOrderByIdAscWithLock(shop.getId(), product.getId());

        BigDecimal totalCost = BigDecimal.ZERO;
        int remainingToConsume = quantity;

        for (ShopInventory lot : lots) {
            if (remainingToConsume <= 0) {
                break;
            }
            int available = remaining(lot);
            if (available <= 0) {
                continue;
            }
            int consumeFromLot = Math.min(available, remainingToConsume);
            lot.setRemainingQuantity(available - consumeFromLot);
            shopInventoryRepository.save(lot);

            totalCost = totalCost.add(lot.getUnitPrice().multiply(BigDecimal.valueOf(consumeFromLot)));
            remainingToConsume -= consumeFromLot;
        }

        int quantityCosted = quantity - remainingToConsume;
        boolean fullyCosted = remainingToConsume == 0;

        if (!fullyCosted) {
            log.warn("FIFO cost layers for shop {} product {} could only cover {} of {} requested units - "
                            + "the uncovered {} units contribute no cost (never guessed)",
                    shop.getId(), product.getId(), quantityCosted, quantity, remainingToConsume);
        }

        recordMovement(shop, product, movementType, quantity,
                quantityCosted > 0 ? totalCost.divide(BigDecimal.valueOf(quantityCosted), 4, java.math.RoundingMode.HALF_UP) : null,
                reference, transactionDate);

        return CostResult.builder()
                .totalCost(totalCost)
                .quantityCosted(quantityCosted)
                .quantityRequested(quantity)
                .fullyCosted(fullyCosted)
                .build();
    }

    // ------------------------------------------------------------------
    // Replenishment (sales return / transfer-in)
    // ------------------------------------------------------------------

    /** Restores quantity to a cost layer at a known unit cost - used when a sales return puts
     * units back on the shelf (at the cost they originally left at, never at selling price -
     * see SalesReturnService) or a transfer-in creates a fresh layer in the destination shop
     * at the cost consumed from the source shop. Always inserts a new lot rather than
     * searching for "the same" lot to top up, since the original lot may have been fully
     * consumed or does not exist in the destination shop at all; the new lot's
     * sourceReference names why it exists (see ShopInventory's class comment). */
    @Transactional
    public ShopInventory restoreCostLayer(Shop shop, Product product, int quantity, BigDecimal unitCost,
                                           Currency currency, InventoryMovementType movementType,
                                           String sourceReference, LocalDate transactionDate) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity to restore must be positive, received: " + quantity);
        }
        if (unitCost == null) {
            throw new IllegalArgumentException("Cannot restore a cost layer without a known unit cost");
        }

        // Idempotent on (shop, product, sourceReference): a retry (e.g. a sales return or
        // transfer-receipt request resubmitted after a timeout) must return the layer already
        // created rather than inserting a second one - see consumeCostLayers' matching guard.
        Optional<ShopInventory> existing = shopInventoryRepository
                .findFirstByShopIdAndProductIdAndSourceReference(shop.getId(), product.getId(), sourceReference);
        if (existing.isPresent()) {
            log.warn("Cost layer restoration for shop {} product {} reference '{}' already recorded - "
                            + "skipping duplicate layer creation (idempotent replay)",
                    shop.getId(), product.getId(), sourceReference);
            return existing.get();
        }

        ShopInventory lot = ShopInventory.builder()
                .shop(shop)
                .product(product)
                .suppliers(null)
                .currency(currency)
                .quantity(quantity)
                .remainingQuantity(quantity)
                .unitPrice(unitCost)
                .sourceReference(sourceReference)
                .build();
        ShopInventory saved = shopInventoryRepository.save(lot);

        recordMovement(shop, product, movementType, quantity, unitCost, sourceReference, transactionDate);

        return saved;
    }

    // ------------------------------------------------------------------
    // Historical backfill
    // ------------------------------------------------------------------

    /** One-time, idempotent normalization of a (shop, product) pair's lots created before FIFO
     * cost layers existed - see the class comment for the exact assumption used. Safe to call
     * on every read/consumption; a no-op once every lot for the pair has a non-null
     * remainingQuantity. */
    @Transactional
    public void backfillLayersIfNeeded(Shop shop, Product product) {
        List<ShopInventory> lots = shopInventoryRepository
                .findAllByShopIdAndProductIdOrderByIdAsc(shop.getId(), product.getId());

        boolean needsBackfill = lots.stream().anyMatch(lot -> lot.getRemainingQuantity() == null);
        if (!needsBackfill) {
            return;
        }

        for (ShopInventory lot : lots) {
            if (lot.getRemainingQuantity() == null) {
                lot.setRemainingQuantity(lot.getQuantity());
            }
        }

        int totalOnHand = inventoryTotalRepository.findByShopAndProduct(shop, product)
                .map(InventoryTotal::getTotalstock).orElse(0);
        int sumRemaining = lots.stream().mapToInt(ShopInventory::getRemainingQuantity).sum();

        if (sumRemaining > totalOnHand) {
            int excessToDeplete = sumRemaining - totalOnHand;
            List<ShopInventory> oldestFirst = lots.stream()
                    .sorted(Comparator.comparing(ShopInventory::getId))
                    .toList();
            for (ShopInventory lot : oldestFirst) {
                if (excessToDeplete <= 0) {
                    break;
                }
                int depleteFromLot = Math.min(lot.getRemainingQuantity(), excessToDeplete);
                lot.setRemainingQuantity(lot.getRemainingQuantity() - depleteFromLot);
                excessToDeplete -= depleteFromLot;
            }
            log.warn("Backfilled FIFO layers for shop {} product {}: lots summed to {} but on-hand is {} - "
                            + "depleted {} units from the oldest lots first (assumed FIFO consumption "
                            + "for pre-existing history, since no record of actual depletion order exists)",
                    shop.getId(), product.getId(), sumRemaining, totalOnHand, sumRemaining - totalOnHand);
        } else if (sumRemaining < totalOnHand) {
            log.warn("Backfilled FIFO layers for shop {} product {}: lots only cover {} of {} on-hand units - "
                            + "the remaining {} units have no cost layer (likely added via a direct stock "
                            + "adjustment) and will value at $0 until a real receipt accounts for them",
                    shop.getId(), product.getId(), sumRemaining, totalOnHand, totalOnHand - sumRemaining);
        }

        shopInventoryRepository.saveAll(lots);
    }

    /** Initializes a brand-new lot's remainingQuantity (always == quantity - nothing consumed
     * yet) and records the RECEIPT audit movement. Called by ShopInventoryService at the one
     * place new ShopInventory rows are created (see its class comment on why that is the sole
     * receipt entry point for both manual and purchase-order-driven receipts). */
    @Transactional
    public void recordReceiptMovement(Shop shop, Product product, int quantity, BigDecimal unitCost,
                                       String reference, LocalDate transactionDate) {
        recordMovement(shop, product, InventoryMovementType.RECEIPT, quantity, unitCost, reference, transactionDate);
    }

    /** Records a quantity movement with no cost dimension - a reservation or its release never
     * has a unit cost (see InventoryMovement's class comment: reserving is not a sale). */
    @Transactional
    public void recordReservationMovement(Shop shop, Product product, InventoryMovementType movementType,
                                           int quantity, String reference, LocalDate transactionDate) {
        if (movementType != InventoryMovementType.RESERVATION && movementType != InventoryMovementType.RESERVATION_RELEASE) {
            throw new IllegalArgumentException("recordReservationMovement only accepts RESERVATION/RESERVATION_RELEASE, got " + movementType);
        }
        recordMovement(shop, product, movementType, quantity, null, reference, transactionDate);
    }

    /** If a movement already exists for this exact (shop, product, movementType, reference),
     * the FIFO consumption it represents already happened - reconstructs the CostResult that
     * call would have returned from the recorded movement rather than consuming layers a second
     * time. A quantity mismatch against the recorded movement is logged (it means the retry's
     * request differs from what was originally recorded, which is unexpected for a genuine
     * retry) but the original recorded outcome is still what is returned - never re-consumed. */
    private Optional<CostResult> replayIfAlreadyRecorded(Shop shop, Product product, int quantity,
                                                           InventoryMovementType movementType, String reference) {
        Optional<InventoryMovement> existing = inventoryMovementRepository
                .findFirstByShopIdAndProductIdAndMovementTypeAndReference(shop.getId(), product.getId(), movementType, reference);
        if (existing.isEmpty()) {
            return Optional.empty();
        }

        InventoryMovement movement = existing.get();
        if (!movement.getQuantity().equals(quantity)) {
            log.warn("Inventory movement {} {} for shop {} product {} reference '{}' was already recorded "
                            + "with quantity {} but this call requested {} - returning the originally recorded "
                            + "outcome rather than consuming layers again",
                    movementType, movement.getId(), shop.getId(), product.getId(), reference,
                    movement.getQuantity(), quantity);
        } else {
            log.warn("Inventory movement {} for shop {} product {} reference '{}' already recorded - "
                            + "skipping duplicate FIFO consumption (idempotent replay)",
                    movementType, shop.getId(), product.getId(), reference);
        }

        boolean fullyCosted = movement.getUnitCost() != null;
        BigDecimal totalCost = fullyCosted
                ? movement.getUnitCost().multiply(BigDecimal.valueOf(movement.getQuantity()))
                : BigDecimal.ZERO;

        return Optional.of(CostResult.builder()
                .totalCost(totalCost)
                .quantityCosted(fullyCosted ? movement.getQuantity() : 0)
                .quantityRequested(quantity)
                .fullyCosted(fullyCosted)
                .build());
    }

    private int remaining(ShopInventory lot) {
        return lot.getRemainingQuantity() != null ? lot.getRemainingQuantity() : 0;
    }

    private void recordMovement(Shop shop, Product product, InventoryMovementType movementType, int quantity,
                                 BigDecimal unitCost, String reference, LocalDate transactionDate) {
        InventoryMovement movement = InventoryMovement.builder()
                .shop(shop)
                .product(product)
                .movementType(movementType)
                .quantity(quantity)
                .unitCost(unitCost)
                .reference(reference)
                .transactionDate(transactionDate != null ? transactionDate : LocalDate.now())
                .build();
        inventoryMovementRepository.save(movement);
    }
}
