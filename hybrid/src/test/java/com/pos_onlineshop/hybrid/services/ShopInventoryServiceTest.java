package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.currency.CurrencyRepository;
import com.pos_onlineshop.hybrid.inventoryTotal.InventoryTotal;
import com.pos_onlineshop.hybrid.inventoryTotal.InventoryTotalRepository;
import com.pos_onlineshop.hybrid.products.Product;
import com.pos_onlineshop.hybrid.products.ProductRepository;
import com.pos_onlineshop.hybrid.shop.Shop;
import com.pos_onlineshop.hybrid.shop.ShopRepository;
import com.pos_onlineshop.hybrid.shopInventory.ShopInventoryRepository;
import com.pos_onlineshop.hybrid.suppliers.SuppliersRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShopInventoryServiceTest {

    @Mock private ShopInventoryRepository shopInventoryRepository;
    @Mock private ShopRepository shopRepository;
    @Mock private ProductRepository productRepository;
    @Mock private SuppliersRepository suppliersRepository;
    @Mock private CurrencyRepository currencyRepository;
    @Mock private InventoryTotalRepository inventoryTotalRepository;
    @Mock private GLPostingService glPostingService;
    @Mock private CurrencyService currencyService;
    @Mock private InventoryValuationService inventoryValuationService;

    private ShopInventoryService service;

    private Shop shop;
    private Product product;

    @BeforeEach
    void setUp() {
        service = new ShopInventoryService(shopInventoryRepository, shopRepository, productRepository,
                suppliersRepository, currencyRepository, inventoryTotalRepository, glPostingService, currencyService,
                inventoryValuationService);

        shop = Shop.builder().id(1L).code("SHOP-001").name("Main Shop").build();
        product = Product.builder().id(1L).name("Widget").build();
    }

    private InventoryTotal inventoryTotal(int total, int reserved) {
        return InventoryTotal.builder().id(1L).shop(shop).product(product)
                .totalstock(total).reservedStock(reserved).build();
    }

    @Test
    void reserveStockIncreasesReservedAndLeavesTotalUnchanged() {
        when(inventoryTotalRepository.findByShopIdAndProductIdWithLock(1L, 1L))
                .thenReturn(Optional.of(inventoryTotal(100, 0)));
        when(inventoryTotalRepository.save(any(InventoryTotal.class))).thenAnswer(inv -> inv.getArgument(0));

        InventoryTotal result = service.reserveStock(1L, 1L, 30);

        assertEquals(100, result.getTotalstock());
        assertEquals(30, result.getReservedStock());
        assertEquals(70, result.getAvailableStock());
    }

    @Test
    void reserveStockRejectsWhenRequestExceedsAvailable() {
        when(inventoryTotalRepository.findByShopIdAndProductIdWithLock(1L, 1L))
                .thenReturn(Optional.of(inventoryTotal(50, 40))); // only 10 available

        assertThrows(RuntimeException.class, () -> service.reserveStock(1L, 1L, 20));
        verify(inventoryTotalRepository, never()).save(any());
    }

    @Test
    void reserveStockRejectsNonPositiveQuantity() {
        assertThrows(IllegalArgumentException.class, () -> service.reserveStock(1L, 1L, 0));
        assertThrows(IllegalArgumentException.class, () -> service.reserveStock(1L, 1L, -5));
        verifyNoInteractions(inventoryTotalRepository);
    }

    @Test
    void reserveStockThrowsWhenNoInventoryTotalRowExists() {
        when(inventoryTotalRepository.findByShopIdAndProductIdWithLock(1L, 1L)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> service.reserveStock(1L, 1L, 5));
    }

    @Test
    void releaseReservationDecreasesReservedOnly() {
        when(inventoryTotalRepository.findByShopIdAndProductIdWithLock(1L, 1L))
                .thenReturn(Optional.of(inventoryTotal(100, 30)));
        when(inventoryTotalRepository.save(any(InventoryTotal.class))).thenAnswer(inv -> inv.getArgument(0));

        InventoryTotal result = service.releaseReservation(1L, 1L, 30);

        assertEquals(100, result.getTotalstock());
        assertEquals(0, result.getReservedStock());
    }

    @Test
    void releaseReservationRejectsReleasingMoreThanReserved() {
        when(inventoryTotalRepository.findByShopIdAndProductIdWithLock(1L, 1L))
                .thenReturn(Optional.of(inventoryTotal(100, 10)));

        assertThrows(IllegalStateException.class, () -> service.releaseReservation(1L, 1L, 20));
        verify(inventoryTotalRepository, never()).save(any());
    }

    @Test
    void commitReservedStockReducesBothReservedAndTotal() {
        when(inventoryTotalRepository.findByShopIdAndProductIdWithLock(1L, 1L))
                .thenReturn(Optional.of(inventoryTotal(100, 30)));
        when(inventoryTotalRepository.save(any(InventoryTotal.class))).thenAnswer(inv -> inv.getArgument(0));

        InventoryTotal result = service.commitReservedStock(1L, 1L, 30);

        assertEquals(70, result.getTotalstock());
        assertEquals(0, result.getReservedStock());
    }

    @Test
    void commitReservedStockRejectsCommittingMoreThanReserved() {
        when(inventoryTotalRepository.findByShopIdAndProductIdWithLock(1L, 1L))
                .thenReturn(Optional.of(inventoryTotal(100, 10)));

        assertThrows(IllegalStateException.class, () -> service.commitReservedStock(1L, 1L, 20));
        verify(inventoryTotalRepository, never()).save(any());
    }

    @Test
    void isInStockChecksAvailableNotRawTotal() {
        when(shopRepository.findById(1L)).thenReturn(Optional.of(shop));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(inventoryTotalRepository.findByShopAndProduct(shop, product))
                .thenReturn(Optional.of(inventoryTotal(100, 95))); // only 5 truly available

        assertFalse(service.isInStock(1L, 1L, 10));
        assertTrue(service.isInStock(1L, 1L, 5));
    }

    @Test
    void reduceStockRejectsGoingNegative() {
        when(shopRepository.findById(1L)).thenReturn(Optional.of(shop));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(inventoryTotalRepository.findByShopIdAndProductIdWithLock(1L, 1L))
                .thenReturn(Optional.of(inventoryTotal(10, 0)));

        assertThrows(RuntimeException.class, () -> service.reduceStock(1L, 1L, 20));
    }

    @Test
    void calculateTotalInventoryValueDelegatesToInventoryValuationService() {
        // The real FIFO cost-layer valuation logic now lives entirely in
        // InventoryValuationServiceTest - this only verifies the delegation itself.
        when(inventoryValuationService.getTotalInventoryValue()).thenReturn(new BigDecimal("400.00"));

        BigDecimal total = service.calculateTotalInventoryValue();

        assertEquals(0, new BigDecimal("400.00").compareTo(total));
        verifyNoInteractions(shopInventoryRepository);
    }
}
