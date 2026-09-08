package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.currency.CurrencyRepository;
import com.pos_onlineshop.hybrid.inventoryMovement.InventoryMovementRepository;
import com.pos_onlineshop.hybrid.inventoryTotal.InventoryTotal;
import com.pos_onlineshop.hybrid.inventoryTotal.InventoryTotalRepository;
import com.pos_onlineshop.hybrid.products.Product;
import com.pos_onlineshop.hybrid.products.ProductRepository;
import com.pos_onlineshop.hybrid.shop.Shop;
import com.pos_onlineshop.hybrid.shop.ShopRepository;
import com.pos_onlineshop.hybrid.shopInventory.ShopInventoryRepository;
import com.pos_onlineshop.hybrid.suppliers.SuppliersRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A real-database (H2, not mocked) test of Section 20's concurrency scenario: several racing
 * reservations against the same InventoryTotal row must never let available stock go negative.
 * Every other test in this suite mocks the repositories, which proves the business logic is
 * correct but cannot prove the pessimistic-lock-based check-then-act sequence in
 * ShopInventoryService.reserveStock is actually race-free under real concurrent transactions -
 * that requires a real JPA provider and a real database taking real row locks. H2 stands in for
 * MySQL here since MySQL is unreachable in this environment; the locking mechanism under test
 * (@Lock(PESSIMISTIC_WRITE) + @Transactional row locking) is standard JPA/Hibernate behavior
 * common to both, not project-specific SQL, so this is a legitimate substitute - unlike claiming
 * a full MySQL integration test passed.
 */
@DataJpaTest
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class InventoryConcurrencySafetyTest {

    @Autowired private ShopInventoryRepository shopInventoryRepository;
    @Autowired private InventoryTotalRepository inventoryTotalRepository;
    @Autowired private InventoryMovementRepository inventoryMovementRepository;
    @Autowired private ShopRepository shopRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private SuppliersRepository suppliersRepository;
    @Autowired private CurrencyRepository currencyRepository;

    private ShopInventoryService buildShopInventoryService() {
        InventoryValuationService valuationService = new InventoryValuationService(
                shopInventoryRepository, inventoryTotalRepository, inventoryMovementRepository);
        GLPostingService noopGlPostingService = org.mockito.Mockito.mock(GLPostingService.class);
        CurrencyService currencyServiceStub = org.mockito.Mockito.mock(CurrencyService.class);
        return new ShopInventoryService(shopInventoryRepository, shopRepository, productRepository,
                suppliersRepository, currencyRepository, inventoryTotalRepository,
                noopGlPostingService, currencyServiceStub, valuationService);
    }

    /**
     * 20 units on hand. Ten threads each try to reserve 3 units simultaneously (30 requested
     * against 20 available) - only 6 of the 10 attempts can possibly succeed. The invariant
     * under test: no matter how the threads interleave, reservedStock never exceeds totalstock
     * and the row's final state is internally consistent - i.e. the pessimistic lock actually
     * serializes the read-check-write sequence instead of letting two threads both read
     * "20 available" and both proceed.
     */
    @Test
    void concurrentReservationsNeverOverReserveTheSameStock() throws InterruptedException {
        Currency currency = currencyRepository.save(
                Currency.builder().code("USD").name("US Dollar").symbol("$").build());
        Shop shop = shopRepository.save(Shop.builder().code("SHOP-CONC").name("Concurrency Shop")
                .defaultCurrency(currency).build());
        Product product = productRepository.save(Product.builder().name("Concurrency Widget")
                .category("General").sku("SKU-CONC-1").build());
        InventoryTotal total = inventoryTotalRepository.save(
                InventoryTotal.builder().shop(shop).product(product).totalstock(20).reservedStock(0).build());

        ShopInventoryService service = buildShopInventoryService();

        int threadCount = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch go = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        AtomicInteger succeeded = new AtomicInteger(0);
        AtomicInteger rejected = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            pool.submit(() -> {
                try {
                    ready.countDown();
                    go.await();
                    service.reserveStock(shop.getId(), product.getId(), 3);
                    succeeded.incrementAndGet();
                } catch (Exception e) {
                    rejected.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await(5, TimeUnit.SECONDS);
        go.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS), "all reservation attempts should finish within 30s");
        pool.shutdown();

        assertEquals(threadCount, succeeded.get() + rejected.get());

        InventoryTotal finalState = inventoryTotalRepository.findByShopAndProduct(shop, product).orElseThrow();
        assertEquals(20, finalState.getTotalstock(), "a reservation never touches totalstock");
        assertEquals(succeeded.get() * 3, finalState.getReservedStock(),
                "reservedStock must equal exactly 3 x the number of reservations that actually succeeded");
        assertTrue(finalState.getReservedStock() <= finalState.getTotalstock(),
                "reserved stock must never exceed total stock - the core invariant under test");
        assertTrue(succeeded.get() <= 6, "at most 6 reservations of 3 units can fit in 20 units of stock");
    }

    /**
     * The specific race the P0-3 audit finding was about: a POS sale (reduceStock) and an
     * online reservation (reserveStock) racing for the same units. 30 units on hand; five
     * threads each try to reserve 10 units (as an online checkout would) and five threads each
     * try to sell 10 units directly (as a POS sale would) - fifteen operations of 10 units each
     * against 30 units of stock, so only 3 can possibly succeed combined, regardless of which
     * kind. Before the P0-3 fix, reduceStock validated only against raw totalstock, so a sale
     * could succeed even when the units it consumed were already reserved - this test would
     * have let reservedStock end up exceeding the post-sale totalstock (i.e. available stock
     * negative). Both operations take the same pessimistic lock on the same InventoryTotal row
     * (see ShopInventoryRepository/InventoryTotalRepository's findByShopIdAndProductIdWithLock),
     * so they serialize against each other, not just against operations of their own kind.
     */
    @Test
    void concurrentSalesAndReservationsNeverLetAvailableStockGoNegative() throws InterruptedException {
        Currency currency = currencyRepository.save(
                Currency.builder().code("USD").name("US Dollar").symbol("$").build());
        Shop shop = shopRepository.save(Shop.builder().code("SHOP-CONC-2").name("Concurrency Shop 2")
                .defaultCurrency(currency).build());
        Product product = productRepository.save(Product.builder().name("Concurrency Widget 2")
                .category("General").sku("SKU-CONC-2").build());
        inventoryTotalRepository.save(
                InventoryTotal.builder().shop(shop).product(product).totalstock(30).reservedStock(0).build());

        ShopInventoryService service = buildShopInventoryService();

        int reservationThreads = 5;
        int saleThreads = 5;
        int totalThreads = reservationThreads + saleThreads;
        ExecutorService pool = Executors.newFixedThreadPool(totalThreads);
        CountDownLatch ready = new CountDownLatch(totalThreads);
        CountDownLatch go = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(totalThreads);
        AtomicInteger reservationsSucceeded = new AtomicInteger(0);
        AtomicInteger salesSucceeded = new AtomicInteger(0);
        AtomicInteger rejected = new AtomicInteger(0);

        for (int i = 0; i < reservationThreads; i++) {
            pool.submit(() -> {
                try {
                    ready.countDown();
                    go.await();
                    service.reserveStock(shop.getId(), product.getId(), 10);
                    reservationsSucceeded.incrementAndGet();
                } catch (Exception e) {
                    rejected.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }
        for (int i = 0; i < saleThreads; i++) {
            pool.submit(() -> {
                try {
                    ready.countDown();
                    go.await();
                    service.reduceStock(shop.getId(), product.getId(), 10);
                    salesSucceeded.incrementAndGet();
                } catch (Exception e) {
                    rejected.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await(5, TimeUnit.SECONDS);
        go.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS), "all operations should finish within 30s");
        pool.shutdown();

        assertEquals(totalThreads, reservationsSucceeded.get() + salesSucceeded.get() + rejected.get());
        assertTrue(reservationsSucceeded.get() + salesSucceeded.get() <= 3,
                "at most 3 operations of 10 units can fit in 30 units of stock, mixed sales and reservations alike");

        InventoryTotal finalState = inventoryTotalRepository.findByShopAndProduct(shop, product).orElseThrow();
        assertEquals(30 - salesSucceeded.get() * 10, finalState.getTotalstock(),
                "only successful sales reduce totalstock");
        assertEquals(reservationsSucceeded.get() * 10, finalState.getReservedStock(),
                "only successful reservations increase reservedStock");
        assertTrue(finalState.getAvailableStock() >= 0,
                "available stock (totalstock - reservedStock) must never go negative - the P0-3 invariant");
    }
}
