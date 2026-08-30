package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.currency.CurrencyRepository;
import com.pos_onlineshop.hybrid.dtos.CreateShopInventoryRequest;
import com.pos_onlineshop.hybrid.inventoryMovement.InventoryMovement;
import com.pos_onlineshop.hybrid.inventoryMovement.InventoryMovementRepository;
import com.pos_onlineshop.hybrid.inventoryTotal.InventoryTotal;
import com.pos_onlineshop.hybrid.inventoryTotal.InventoryTotalRepository;
import com.pos_onlineshop.hybrid.products.Product;
import com.pos_onlineshop.hybrid.products.ProductRepository;
import com.pos_onlineshop.hybrid.shop.Shop;
import com.pos_onlineshop.hybrid.shop.ShopRepository;
import com.pos_onlineshop.hybrid.shopInventory.ShopInventory;
import com.pos_onlineshop.hybrid.shopInventory.ShopInventoryRepository;
import com.pos_onlineshop.hybrid.suppliers.Suppliers;
import com.pos_onlineshop.hybrid.suppliers.SuppliersRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

/**
 * End-to-end transcription of the master prompt's Section 30 "Acceptance Example": receive,
 * sell, reserve, then fulfill the reservation, asserting on-hand/reserved/available quantity,
 * FIFO valuation, and COGS at every step. Wires REAL {@link ShopInventoryService} and
 * {@link InventoryValuationService} instances together (the two authorities this whole
 * architecture depends on agreeing with each other) rather than mocking one against the other.
 * Only the repositories are faked - as small, stateful, hand-rolled in-memory substitutes over
 * plain Mockito mocks - and only GL posting/currency lookups are simple mocks, since GL posting
 * mechanics are already covered by ShopInventoryServiceTest/OrderServiceTest/POSServiceTest.
 */
@ExtendWith(MockitoExtension.class)
class InventoryAccountingAcceptanceTest {

    @Mock private ShopRepository shopRepository;
    @Mock private ProductRepository productRepository;
    @Mock private SuppliersRepository suppliersRepository;
    @Mock private CurrencyRepository currencyRepository;
    @Mock private GLPostingService glPostingService;
    @Mock private CurrencyService currencyService;

    @Mock private ShopInventoryRepository shopInventoryRepository;
    @Mock private InventoryTotalRepository inventoryTotalRepository;
    @Mock private InventoryMovementRepository inventoryMovementRepository;

    private ShopInventoryService shopInventoryService;
    private InventoryValuationService inventoryValuationService;

    private Shop shop;
    private Product product;
    private Suppliers supplier;
    private Currency currency;

    // In-memory backing state for the faked repositories.
    private final List<ShopInventory> lots = new ArrayList<>();
    private final AtomicLong lotIdSequence = new AtomicLong(1);
    private InventoryTotal total;
    private final List<InventoryMovement> movements = new ArrayList<>();

    @BeforeEach
    void setUp() {
        shop = Shop.builder().id(1L).code("SHOP-001").name("Main Shop").build();
        product = Product.builder().id(1L).name("Widget").build();
        supplier = Suppliers.builder().id(1L).name("Acme Supplies").build();
        currency = Currency.builder().id(1L).code("USD").build();

        lenient().when(shopRepository.findById(1L)).thenReturn(Optional.of(shop));
        lenient().when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        lenient().when(suppliersRepository.findById(1L)).thenReturn(Optional.of(supplier));
        lenient().when(currencyRepository.findById(1L)).thenReturn(Optional.of(currency));
        lenient().when(currencyService.getBaseCurrency()).thenReturn(currency);

        stubShopInventoryRepository();
        stubInventoryTotalRepository();
        stubInventoryMovementRepository();

        inventoryValuationService = new InventoryValuationService(
                shopInventoryRepository, inventoryTotalRepository, inventoryMovementRepository);

        shopInventoryService = new ShopInventoryService(
                shopInventoryRepository, shopRepository, productRepository, suppliersRepository,
                currencyRepository, inventoryTotalRepository, glPostingService, currencyService,
                inventoryValuationService);
    }

    private void stubShopInventoryRepository() {
        lenient().when(shopInventoryRepository.save(any(ShopInventory.class))).thenAnswer(invocation -> {
            ShopInventory lot = invocation.getArgument(0);
            if (lot.getId() == null) {
                lot.setId(lotIdSequence.getAndIncrement());
                lots.add(lot);
            } else {
                lots.replaceAll(existing -> existing.getId().equals(lot.getId()) ? lot : existing);
            }
            return lot;
        });
        lenient().when(shopInventoryRepository.saveAll(any())).thenAnswer(invocation -> {
            List<ShopInventory> saved = invocation.getArgument(0);
            saved.forEach(shopInventoryRepository::save);
            return saved;
        });
        lenient().when(shopInventoryRepository.findAllByShopIdAndProductIdOrderByIdAsc(eq(1L), eq(1L)))
                .thenAnswer(invocation -> sortedLots());
        lenient().when(shopInventoryRepository.findAllByShopIdAndProductIdOrderByIdAscWithLock(eq(1L), eq(1L)))
                .thenAnswer(invocation -> sortedLots());
    }

    private List<ShopInventory> sortedLots() {
        return lots.stream().sorted(Comparator.comparing(ShopInventory::getId)).toList();
    }

    private void stubInventoryTotalRepository() {
        lenient().when(inventoryTotalRepository.findByShopIdAndProductIdWithLock(eq(1L), eq(1L)))
                .thenAnswer(invocation -> Optional.ofNullable(total));
        lenient().when(inventoryTotalRepository.findByShopAndProduct(eq(shop), eq(product)))
                .thenAnswer(invocation -> Optional.ofNullable(total));
        lenient().when(inventoryTotalRepository.findAllWithShopAndProduct())
                .thenAnswer(invocation -> total == null ? List.<InventoryTotal>of() : List.of(total));
        lenient().when(inventoryTotalRepository.save(any(InventoryTotal.class))).thenAnswer(invocation -> {
            total = invocation.getArgument(0);
            return total;
        });
    }

    private void stubInventoryMovementRepository() {
        lenient().when(inventoryMovementRepository.save(any(InventoryMovement.class))).thenAnswer(invocation -> {
            InventoryMovement movement = invocation.getArgument(0);
            movements.add(movement);
            return movement;
        });
    }

    @Test
    void receiveSellReserveAndFulfillMatchTheAcceptanceExample() {
        // --- Receive 100 units @ $10.00 ---
        CreateShopInventoryRequest receipt = CreateShopInventoryRequest.builder()
                .shopId(1L).productId(1L).supplierId(1L).currencyId(1L)
                .quantity(100).unitPrice(new BigDecimal("10.00"))
                .build();
        shopInventoryService.createShopInventory(receipt);

        assertEquals(100, total.getTotalstock());
        assertEquals(0, total.getReservedStock());
        assertEquals(100, total.getAvailableStock());
        assertEquals(0, new BigDecimal("1000.00").compareTo(inventoryValuationService.getInventoryValue(shop, product)));
        assertEquals(0, new BigDecimal("1000.00").compareTo(inventoryValuationService.getTotalInventoryValue()));

        // --- Sell 60 units (POS: reduceStock + FIFO cost) ---
        shopInventoryService.reduceStock(1L, 1L, 60);
        InventoryValuationService.CostResult saleCost =
                inventoryValuationService.getCostForSale(shop, product, 60, "POS-SALE-1");

        assertEquals(40, total.getTotalstock());
        assertEquals(40, total.getAvailableStock());
        assertTrue(saleCost.isFullyCosted());
        assertEquals(0, new BigDecimal("600.00").compareTo(saleCost.getTotalCost()));
        assertEquals(0, new BigDecimal("400.00").compareTo(inventoryValuationService.getInventoryValue(shop, product)));

        // --- Reserve 10 units for an online order: quantity moves, GL must NOT be touched ---
        shopInventoryService.reserveStock(1L, 1L, 10);

        assertEquals(40, total.getTotalstock(), "a reservation alone must never reduce on-hand stock");
        assertEquals(10, total.getReservedStock());
        assertEquals(30, total.getAvailableStock());
        assertEquals(0, new BigDecimal("400.00").compareTo(inventoryValuationService.getInventoryValue(shop, product)),
                "inventory value must be unaffected by a reservation - no cost layer was consumed");
        // Only the original receipt posted to the GL - the reservation itself must never post.
        verify(glPostingService, org.mockito.Mockito.times(1)).post(any());

        // --- Fulfill the reservation: commit stock + record the deferred COGS ---
        shopInventoryService.commitReservedStock(1L, 1L, 10);
        InventoryValuationService.CostResult fulfillmentCost =
                inventoryValuationService.getCostForSale(shop, product, 10, "ONLINE-ORDER-1");

        assertEquals(30, total.getTotalstock());
        assertEquals(0, total.getReservedStock());
        assertEquals(30, total.getAvailableStock());
        assertTrue(fulfillmentCost.isFullyCosted());
        assertEquals(0, new BigDecimal("100.00").compareTo(fulfillmentCost.getTotalCost()));
        assertEquals(0, new BigDecimal("300.00").compareTo(inventoryValuationService.getInventoryValue(shop, product)));
        assertEquals(0, new BigDecimal("300.00").compareTo(inventoryValuationService.getTotalInventoryValue()));

        // --- Movement audit trail recorded every quantity-affecting step, correctly typed ---
        assertTrue(movements.stream().anyMatch(m -> m.getMovementType() == com.pos_onlineshop.hybrid.enums.InventoryMovementType.RECEIPT));
        assertTrue(movements.stream().anyMatch(m -> m.getMovementType() == com.pos_onlineshop.hybrid.enums.InventoryMovementType.SALE));
        assertTrue(movements.stream().anyMatch(m -> m.getMovementType() == com.pos_onlineshop.hybrid.enums.InventoryMovementType.RESERVATION));
    }

    @Test
    void fifoConsumesOldestLotFirstAcrossTwoLotsAtDifferentCosts() {
        shopInventoryService.createShopInventory(CreateShopInventoryRequest.builder()
                .shopId(1L).productId(1L).supplierId(1L).currencyId(1L)
                .quantity(50).unitPrice(new BigDecimal("10.00")).build());
        shopInventoryService.createShopInventory(CreateShopInventoryRequest.builder()
                .shopId(1L).productId(1L).supplierId(1L).currencyId(1L)
                .quantity(50).unitPrice(new BigDecimal("12.00")).build());

        assertEquals(100, total.getTotalstock());
        assertEquals(0, new BigDecimal("1100.00").compareTo(inventoryValuationService.getTotalInventoryValue()));

        InventoryValuationService.CostResult cost =
                inventoryValuationService.getCostForSale(shop, product, 60, "POS-SALE-2");

        // 50 units @ $10 (oldest lot exhausted) + 10 units @ $12 (next lot) = $620
        assertEquals(0, new BigDecimal("620.00").compareTo(cost.getTotalCost()));
        assertTrue(cost.isFullyCosted());
    }

    @Test
    void reservationNeverPostsToTheGeneralLedgerEvenWhenReleased() {
        shopInventoryService.createShopInventory(CreateShopInventoryRequest.builder()
                .shopId(1L).productId(1L).supplierId(1L).currencyId(1L)
                .quantity(20).unitPrice(new BigDecimal("5.00")).build());

        shopInventoryService.reserveStock(1L, 1L, 5);
        shopInventoryService.releaseReservation(1L, 1L, 5);

        assertEquals(20, total.getTotalstock());
        assertEquals(0, total.getReservedStock());
        // Only the receipt should have posted to the GL - reserving and releasing never do.
        verify(glPostingService, org.mockito.Mockito.times(1)).post(any());
    }
}
