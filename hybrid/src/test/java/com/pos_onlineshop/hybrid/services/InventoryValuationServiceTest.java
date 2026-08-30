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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryValuationServiceTest {

    @Mock private ShopInventoryRepository shopInventoryRepository;
    @Mock private InventoryTotalRepository inventoryTotalRepository;
    @Mock private InventoryMovementRepository inventoryMovementRepository;

    private InventoryValuationService service;

    private Shop shop;
    private Product product;
    private Currency currency;

    @BeforeEach
    void setUp() {
        service = new InventoryValuationService(shopInventoryRepository, inventoryTotalRepository, inventoryMovementRepository);
        shop = Shop.builder().id(1L).code("SHOP-001").name("Main Shop").build();
        product = Product.builder().id(1L).name("Widget").build();
        currency = Currency.builder().id(1L).code("USD").build();

        lenient().when(shopInventoryRepository.save(any(ShopInventory.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(inventoryMovementRepository.save(any(InventoryMovement.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private ShopInventory lot(long id, int quantity, int remainingQuantity, String unitPrice) {
        return ShopInventory.builder().id(id).shop(shop).product(product).currency(currency)
                .quantity(quantity).remainingQuantity(remainingQuantity)
                .unitPrice(new BigDecimal(unitPrice)).build();
    }

    // ------------------------------------------------------------------
    // FIFO consumption (getCostForSale / consumeCostLayers)
    // ------------------------------------------------------------------

    @Test
    void getCostForSaleConsumesASingleLotFully() {
        ShopInventory lotA = lot(1L, 100, 100, "10.00");
        when(shopInventoryRepository.findAllByShopIdAndProductIdOrderByIdAscWithLock(1L, 1L)).thenReturn(List.of(lotA));
        when(shopInventoryRepository.findAllByShopIdAndProductIdOrderByIdAsc(1L, 1L)).thenReturn(List.of(lotA));

        InventoryValuationService.CostResult result = service.getCostForSale(shop, product, 60, "ORDER-1");

        assertEquals(0, new BigDecimal("600.00").compareTo(result.getTotalCost()));
        assertTrue(result.isFullyCosted());
        assertEquals(60, result.getQuantityCosted());
        assertEquals(40, lotA.getRemainingQuantity());
    }

    @Test
    void getCostForSaleFollowsFifoAcrossTwoLotsAtDifferentCosts() {
        // The master worked example: 100 @ $10, 50 @ $12, sell 120 -> COGS = 1000 + 240 = 1240,
        // remaining 30 units all in the second lot @ $12 = $360.
        ShopInventory lotA = lot(1L, 100, 100, "10.00");
        ShopInventory lotB = lot(2L, 50, 50, "12.00");
        when(shopInventoryRepository.findAllByShopIdAndProductIdOrderByIdAscWithLock(1L, 1L)).thenReturn(List.of(lotA, lotB));
        when(shopInventoryRepository.findAllByShopIdAndProductIdOrderByIdAsc(1L, 1L)).thenReturn(List.of(lotA, lotB));

        InventoryValuationService.CostResult result = service.getCostForSale(shop, product, 120, "ORDER-2");

        assertEquals(0, new BigDecimal("1240.00").compareTo(result.getTotalCost()));
        assertTrue(result.isFullyCosted());
        assertEquals(0, lotA.getRemainingQuantity());
        assertEquals(30, lotB.getRemainingQuantity()); // 50 - 20 consumed from this lot = 30

        BigDecimal remainingValue = service.getInventoryValue(shop, product);
        assertEquals(0, new BigDecimal("360.00").compareTo(remainingValue)); // 30 @ 12.00 - matches the master worked example
    }

    @Test
    void getCostForSaleSkipsFullyConsumedLotsAndMovesToTheNextOne() {
        ShopInventory exhausted = lot(1L, 50, 0, "8.00");
        ShopInventory fresh = lot(2L, 30, 30, "9.00");
        when(shopInventoryRepository.findAllByShopIdAndProductIdOrderByIdAscWithLock(1L, 1L))
                .thenReturn(List.of(exhausted, fresh));

        InventoryValuationService.CostResult result = service.consumeCostLayers(
                shop, product, 10, InventoryMovementType.SALE, "ORDER-3", LocalDate.of(2026, 1, 1));

        assertEquals(0, new BigDecimal("90.00").compareTo(result.getTotalCost()));
        assertEquals(20, fresh.getRemainingQuantity());
        verify(shopInventoryRepository, never()).save(exhausted);
    }

    @Test
    void getCostForSaleReturnsPartialCostWhenLayersDontFullyCoverTheQuantity() {
        ShopInventory lotA = lot(1L, 10, 10, "5.00");
        when(shopInventoryRepository.findAllByShopIdAndProductIdOrderByIdAscWithLock(1L, 1L)).thenReturn(List.of(lotA));

        InventoryValuationService.CostResult result = service.getCostForSale(shop, product, 25, "ORDER-4");

        assertFalse(result.isFullyCosted());
        assertEquals(10, result.getQuantityCosted());
        assertEquals(25, result.getQuantityRequested());
        assertEquals(0, new BigDecimal("50.00").compareTo(result.getTotalCost())); // only the covered 10 units
        assertEquals(0, lotA.getRemainingQuantity());
    }

    @Test
    void getCostForSaleWithNoLayersAtAllReturnsZeroCostAndNotFullyCosted() {
        when(shopInventoryRepository.findAllByShopIdAndProductIdOrderByIdAscWithLock(1L, 1L)).thenReturn(List.of());

        InventoryValuationService.CostResult result = service.getCostForSale(shop, product, 5, "ORDER-5");

        assertFalse(result.isFullyCosted());
        assertEquals(0, result.getQuantityCosted());
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getTotalCost()));
    }

    @Test
    void getCostForSaleRejectsNonPositiveQuantity() {
        assertThrows(IllegalArgumentException.class, () -> service.getCostForSale(shop, product, 0, "ORDER-6"));
        assertThrows(IllegalArgumentException.class, () -> service.getCostForSale(shop, product, -3, "ORDER-6"));
    }

    @Test
    void getCostForSaleRecordsAnAuditMovementWithTheWeightedUnitCost() {
        ShopInventory lotA = lot(1L, 100, 100, "10.00");
        ShopInventory lotB = lot(2L, 50, 50, "12.00");
        when(shopInventoryRepository.findAllByShopIdAndProductIdOrderByIdAscWithLock(1L, 1L)).thenReturn(List.of(lotA, lotB));

        service.getCostForSale(shop, product, 120, "ORDER-7");

        ArgumentCaptor<InventoryMovement> captor = ArgumentCaptor.forClass(InventoryMovement.class);
        verify(inventoryMovementRepository).save(captor.capture());
        InventoryMovement movement = captor.getValue();
        assertEquals(InventoryMovementType.SALE, movement.getMovementType());
        assertEquals(120, movement.getQuantity());
        assertEquals("ORDER-7", movement.getReference());
        // 1240 / 120 = 10.3333...
        assertEquals(0, new BigDecimal("10.3333").compareTo(movement.getUnitCost()));
    }

    // ------------------------------------------------------------------
    // Idempotency (retry safety - Section 21)
    // ------------------------------------------------------------------

    @Test
    void consumeCostLayersSkipsFifoConsumptionWhenTheSameReferenceWasAlreadyRecorded() {
        InventoryMovement existing = InventoryMovement.builder().id(9L).shop(shop).product(product)
                .movementType(InventoryMovementType.SALE).quantity(60)
                .unitCost(new BigDecimal("10.00")).reference("ORDER-1-LINE-5")
                .transactionDate(LocalDate.of(2026, 1, 1)).build();
        when(inventoryMovementRepository.findFirstByShopIdAndProductIdAndMovementTypeAndReference(
                1L, 1L, InventoryMovementType.SALE, "ORDER-1-LINE-5")).thenReturn(Optional.of(existing));

        InventoryValuationService.CostResult result = service.getCostForSale(shop, product, 60, "ORDER-1-LINE-5");

        assertTrue(result.isFullyCosted());
        assertEquals(60, result.getQuantityCosted());
        assertEquals(0, new BigDecimal("600.00").compareTo(result.getTotalCost()));
        // No layers were touched and no new movement was written - this was a pure replay.
        verifyNoInteractions(shopInventoryRepository);
        verify(inventoryMovementRepository, never()).save(any());
    }

    @Test
    void consumeCostLayersDoesNotDedupeDifferentLinesOfTheSameOrderForTheSameProduct() {
        // Two order lines for the same product in the same order must each get their own
        // reference (see POSService/OrderService) - otherwise the second line's consumption
        // would be wrongly treated as a duplicate of the first and silently skipped.
        ShopInventory lotA = lot(1L, 100, 100, "10.00");
        when(shopInventoryRepository.findAllByShopIdAndProductIdOrderByIdAscWithLock(1L, 1L)).thenReturn(List.of(lotA));
        when(inventoryMovementRepository.findFirstByShopIdAndProductIdAndMovementTypeAndReference(
                eq(1L), eq(1L), eq(InventoryMovementType.SALE), anyString())).thenReturn(Optional.empty());

        service.getCostForSale(shop, product, 20, "ORDER-1-LINE-5");
        service.getCostForSale(shop, product, 30, "ORDER-1-LINE-6");

        assertEquals(50, lotA.getRemainingQuantity());
        verify(inventoryMovementRepository, times(2)).save(any());
    }

    @Test
    void restoreCostLayerSkipsCreatingASecondLotWhenTheSameSourceReferenceWasAlreadyRecorded() {
        ShopInventory existingLot = lot(3L, 5, 5, "6.00");
        existingLot.setSourceReference("SALES_RETURN-RET-1-LINE-9");
        when(shopInventoryRepository.findFirstByShopIdAndProductIdAndSourceReference(1L, 1L, "SALES_RETURN-RET-1-LINE-9"))
                .thenReturn(Optional.of(existingLot));

        ShopInventory result = service.restoreCostLayer(shop, product, 5, new BigDecimal("6.00"), currency,
                InventoryMovementType.SALE_RETURN, "SALES_RETURN-RET-1-LINE-9", LocalDate.of(2026, 8, 15));

        assertSame(existingLot, result);
        verify(shopInventoryRepository, never()).save(any());
        verify(inventoryMovementRepository, never()).save(any());
    }

    // ------------------------------------------------------------------
    // Valuation reads
    // ------------------------------------------------------------------

    @Test
    void getInventoryValueSumsRemainingQuantityAcrossAllLotsForAPair() {
        ShopInventory lotA = lot(1L, 100, 40, "10.00");
        ShopInventory lotB = lot(2L, 50, 50, "12.00");
        when(shopInventoryRepository.findAllByShopIdAndProductIdOrderByIdAsc(1L, 1L)).thenReturn(List.of(lotA, lotB));
        when(inventoryTotalRepository.findByShopAndProduct(shop, product))
                .thenReturn(Optional.of(InventoryTotal.builder().shop(shop).product(product).totalstock(90).build()));

        BigDecimal value = service.getInventoryValue(shop, product);

        // 40 @ 10 + 50 @ 12 = 400 + 600 = 1000
        assertEquals(0, new BigDecimal("1000.00").compareTo(value));
    }

    @Test
    void getInventoryValueDetailedReportsUnvaluedQuantityRatherThanHidingIt() {
        ShopInventory lotA = lot(1L, 100, 40, "10.00");
        when(shopInventoryRepository.findAllByShopIdAndProductIdOrderByIdAsc(1L, 1L)).thenReturn(List.of(lotA));
        // On-hand is 60, but lots only cover 40 - 20 units have no cost layer.
        when(inventoryTotalRepository.findByShopAndProduct(shop, product))
                .thenReturn(Optional.of(InventoryTotal.builder().shop(shop).product(product).totalstock(60).build()));

        InventoryValuationService.ValuationResult result = service.getInventoryValueDetailed(shop, product);

        assertEquals(0, new BigDecimal("400.00").compareTo(result.getTotalValue()));
        assertEquals(40, result.getValuedQuantity());
        assertEquals(20, result.getUnvaluedQuantity());
    }

    @Test
    void getTotalInventoryValueSkipsPairsWithNoStockOnHand() {
        InventoryTotal zeroStock = InventoryTotal.builder().shop(shop).product(product).totalstock(0).build();
        when(inventoryTotalRepository.findAllWithShopAndProduct()).thenReturn(List.of(zeroStock));

        BigDecimal total = service.getTotalInventoryValue();

        assertEquals(0, BigDecimal.ZERO.compareTo(total));
        verifyNoInteractions(shopInventoryRepository);
    }

    @Test
    void getUnitCostReturnsTheOldestLotWithRemainingStock() {
        ShopInventory exhausted = lot(1L, 50, 0, "8.00");
        ShopInventory fresh = lot(2L, 30, 30, "9.00");
        when(shopInventoryRepository.findAllByShopIdAndProductIdOrderByIdAsc(1L, 1L)).thenReturn(List.of(exhausted, fresh));

        Optional<BigDecimal> unitCost = service.getUnitCost(shop, product);

        assertTrue(unitCost.isPresent());
        assertEquals(0, new BigDecimal("9.00").compareTo(unitCost.get()));
    }

    @Test
    void getUnitCostIsEmptyWhenNoLotHasRemainingStock() {
        when(shopInventoryRepository.findAllByShopIdAndProductIdOrderByIdAsc(1L, 1L)).thenReturn(List.of());

        assertTrue(service.getUnitCost(shop, product).isEmpty());
    }

    // ------------------------------------------------------------------
    // Replenishment (restoreCostLayer)
    // ------------------------------------------------------------------

    @Test
    void restoreCostLayerCreatesANewLotAtTheGivenCostWithNoSupplier() {
        ShopInventory restored = service.restoreCostLayer(shop, product, 2, new BigDecimal("6.00"), currency,
                InventoryMovementType.SALE_RETURN, "SALES_RETURN-RET-1", LocalDate.of(2026, 8, 15));

        assertEquals(2, restored.getQuantity());
        assertEquals(2, restored.getRemainingQuantity());
        assertNull(restored.getSuppliers());
        assertEquals("SALES_RETURN-RET-1", restored.getSourceReference());
        assertEquals(0, new BigDecimal("6.00").compareTo(restored.getUnitPrice()));

        ArgumentCaptor<InventoryMovement> captor = ArgumentCaptor.forClass(InventoryMovement.class);
        verify(inventoryMovementRepository).save(captor.capture());
        assertEquals(InventoryMovementType.SALE_RETURN, captor.getValue().getMovementType());
    }

    @Test
    void restoreCostLayerRejectsAMissingUnitCost() {
        assertThrows(IllegalArgumentException.class, () -> service.restoreCostLayer(
                shop, product, 2, null, currency, InventoryMovementType.SALE_RETURN, "REF", LocalDate.now()));
    }

    @Test
    void restoreCostLayerRejectsNonPositiveQuantity() {
        assertThrows(IllegalArgumentException.class, () -> service.restoreCostLayer(
                shop, product, 0, new BigDecimal("6.00"), currency, InventoryMovementType.SALE_RETURN, "REF", LocalDate.now()));
    }

    // ------------------------------------------------------------------
    // Historical backfill
    // ------------------------------------------------------------------

    @Test
    void backfillInitializesNullRemainingQuantityToFullQuantityWhenItMatchesOnHand() {
        ShopInventory legacy = ShopInventory.builder().id(1L).shop(shop).product(product).currency(currency)
                .quantity(40).remainingQuantity(null).unitPrice(new BigDecimal("10.00")).build();
        when(shopInventoryRepository.findAllByShopIdAndProductIdOrderByIdAsc(1L, 1L)).thenReturn(List.of(legacy));
        when(inventoryTotalRepository.findByShopAndProduct(shop, product))
                .thenReturn(Optional.of(InventoryTotal.builder().shop(shop).product(product).totalstock(40).build()));

        service.backfillLayersIfNeeded(shop, product);

        assertEquals(40, legacy.getRemainingQuantity());
    }

    @Test
    void backfillDepletesOldestLotsFirstWhenLegacyLotsOversumOnHandStock() {
        // Two legacy lots totalling 150 units of history, but only 40 units are actually on
        // hand today - assume FIFO depletion already happened against the oldest lot first.
        ShopInventory older = ShopInventory.builder().id(1L).shop(shop).product(product).currency(currency)
                .quantity(100).remainingQuantity(null).unitPrice(new BigDecimal("10.00")).build();
        ShopInventory newer = ShopInventory.builder().id(2L).shop(shop).product(product).currency(currency)
                .quantity(50).remainingQuantity(null).unitPrice(new BigDecimal("12.00")).build();
        when(shopInventoryRepository.findAllByShopIdAndProductIdOrderByIdAsc(1L, 1L)).thenReturn(List.of(older, newer));
        when(inventoryTotalRepository.findByShopAndProduct(shop, product))
                .thenReturn(Optional.of(InventoryTotal.builder().shop(shop).product(product).totalstock(40).build()));

        service.backfillLayersIfNeeded(shop, product);

        assertEquals(0, older.getRemainingQuantity());
        assertEquals(40, newer.getRemainingQuantity());
    }

    @Test
    void backfillIsANoOpOnceEveryLotAlreadyHasARemainingQuantity() {
        ShopInventory alreadyMigrated = lot(1L, 100, 40, "10.00");
        when(shopInventoryRepository.findAllByShopIdAndProductIdOrderByIdAsc(1L, 1L)).thenReturn(List.of(alreadyMigrated));

        service.backfillLayersIfNeeded(shop, product);

        verify(shopInventoryRepository, never()).saveAll(any());
        verifyNoInteractions(inventoryTotalRepository);
    }

    @Test
    void backfillLeavesAnUnlotedGapWhenOnHandExceedsRecordedLots() {
        // On-hand is 60 but the only lot on record covers 40 - some stock was added without
        // ever creating a lot (e.g. a direct manual adjustment). The gap must not be invented.
        ShopInventory onlyLot = ShopInventory.builder().id(1L).shop(shop).product(product).currency(currency)
                .quantity(40).remainingQuantity(null).unitPrice(new BigDecimal("10.00")).build();
        when(shopInventoryRepository.findAllByShopIdAndProductIdOrderByIdAsc(1L, 1L)).thenReturn(List.of(onlyLot));
        when(inventoryTotalRepository.findByShopAndProduct(shop, product))
                .thenReturn(Optional.of(InventoryTotal.builder().shop(shop).product(product).totalstock(60).build()));

        service.backfillLayersIfNeeded(shop, product);

        assertEquals(40, onlyLot.getRemainingQuantity());
    }
}
