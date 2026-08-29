package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.dtos.InventoryBalanceResponse;
import com.pos_onlineshop.hybrid.dtos.InventoryMovementResponse;
import com.pos_onlineshop.hybrid.dtos.InventoryReconciliationReport;
import com.pos_onlineshop.hybrid.dtos.InventoryValuationResponse;
import com.pos_onlineshop.hybrid.enums.InventoryMovementType;
import com.pos_onlineshop.hybrid.inventoryMovement.InventoryMovement;
import com.pos_onlineshop.hybrid.inventoryMovement.InventoryMovementRepository;
import com.pos_onlineshop.hybrid.inventoryTotal.InventoryTotal;
import com.pos_onlineshop.hybrid.inventoryTotal.InventoryTotalRepository;
import com.pos_onlineshop.hybrid.products.Product;
import com.pos_onlineshop.hybrid.shop.Shop;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryReportServiceTest {

    @Mock private InventoryTotalRepository inventoryTotalRepository;
    @Mock private InventoryValuationService inventoryValuationService;
    @Mock private InventoryMovementRepository inventoryMovementRepository;
    @Mock private ControlAccountReconciliationService controlAccountReconciliationService;

    private InventoryReportService service;

    private Shop shop;
    private Product product;

    @BeforeEach
    void setUp() {
        service = new InventoryReportService(inventoryTotalRepository, inventoryValuationService,
                inventoryMovementRepository, controlAccountReconciliationService);
        shop = Shop.builder().id(1L).code("SHOP-001").name("Main Shop").build();
        product = Product.builder().id(1L).name("Widget").build();
    }

    private InventoryTotal total(int stock, int reserved) {
        return InventoryTotal.builder().shop(shop).product(product).totalstock(stock).reservedStock(reserved).build();
    }

    @Test
    void getBalancesReportsOnHandReservedAndAvailable() {
        when(inventoryTotalRepository.findAllWithShopAndProduct()).thenReturn(List.of(total(100, 30)));

        List<InventoryBalanceResponse> balances = service.getBalances(null);

        assertEquals(1, balances.size());
        InventoryBalanceResponse b = balances.get(0);
        assertEquals(100, b.getOnHand());
        assertEquals(30, b.getReserved());
        assertEquals(70, b.getAvailable());
    }

    @Test
    void getBalancesFiltersByShopWhenGiven() {
        when(inventoryTotalRepository.findByShopIdWithDetails(1L)).thenReturn(List.of(total(50, 0)));

        service.getBalances(1L);

        verify(inventoryTotalRepository).findByShopIdWithDetails(1L);
        verify(inventoryTotalRepository, never()).findAllWithShopAndProduct();
    }

    @Test
    void getValuationSkipsPairsWithNoStockOnHand() {
        when(inventoryTotalRepository.findAllWithShopAndProduct()).thenReturn(List.of(total(0, 0)));

        List<InventoryValuationResponse> valuation = service.getValuation(null);

        assertTrue(valuation.isEmpty());
        verifyNoInteractions(inventoryValuationService);
    }

    @Test
    void getValuationReportsRealFifoValueAndUnvaluedQuantity() {
        when(inventoryTotalRepository.findAllWithShopAndProduct()).thenReturn(List.of(total(40, 0)));
        when(inventoryValuationService.getInventoryValueDetailed(shop, product))
                .thenReturn(InventoryValuationService.ValuationResult.builder()
                        .totalValue(new BigDecimal("300.00")).valuedQuantity(30).unvaluedQuantity(10).build());
        when(inventoryValuationService.getUnitCost(shop, product)).thenReturn(Optional.of(new BigDecimal("10.00")));

        List<InventoryValuationResponse> valuation = service.getValuation(null);

        InventoryValuationResponse v = valuation.get(0);
        assertEquals(0, new BigDecimal("300.00").compareTo(v.getInventoryValue()));
        assertEquals(10, v.getUnvaluedQuantity());
        assertEquals(0, new BigDecimal("10.00").compareTo(v.getUnitCost()));
    }

    @Test
    void getMovementsFiltersByShopAndProductWhenBothGiven() {
        InventoryMovement movement = InventoryMovement.builder().id(1L).shop(shop).product(product)
                .movementType(InventoryMovementType.SALE).quantity(5).reference("ORDER-1")
                .transactionDate(LocalDate.of(2026, 1, 1)).build();
        when(inventoryMovementRepository.findByShopIdAndProductIdOrderByIdAsc(1L, 1L)).thenReturn(List.of(movement));

        List<InventoryMovementResponse> movements = service.getMovements(1L, 1L);

        assertEquals(1, movements.size());
        assertEquals("SALE", movements.get(0).getMovementType());
        assertEquals("ORDER-1", movements.get(0).getReference());
    }

    @Test
    void getMovementsFallsBackToAllWhenNoFilterGiven() {
        when(inventoryMovementRepository.findAllByOrderByIdDesc()).thenReturn(List.of());

        service.getMovements(null, null);

        verify(inventoryMovementRepository).findAllByOrderByIdDesc();
    }

    @Test
    void getReconciliationReportsReconciledWhenValueMatchesGlBalance() {
        when(inventoryTotalRepository.findAllWithShopAndProduct()).thenReturn(List.of(total(100, 20)));
        when(inventoryValuationService.getTotalInventoryValue()).thenReturn(new BigDecimal("1000.00"));
        when(controlAccountReconciliationService.getInventoryAssetGlBalance(LocalDate.of(2026, 1, 1)))
                .thenReturn(new BigDecimal("1000.00"));
        when(inventoryValuationService.getInventoryValueDetailed(shop, product))
                .thenReturn(InventoryValuationService.ValuationResult.builder()
                        .totalValue(new BigDecimal("1000.00")).valuedQuantity(100).unvaluedQuantity(0).build());
        when(inventoryValuationService.getUnitCost(shop, product)).thenReturn(Optional.of(new BigDecimal("10.00")));

        InventoryReconciliationReport report = service.getReconciliation(LocalDate.of(2026, 1, 1));

        assertEquals(100, report.getOnHandQuantity());
        assertEquals(20, report.getReservedQuantity());
        assertEquals(80, report.getAvailableQuantity());
        assertEquals(0, BigDecimal.ZERO.compareTo(report.getVariance()));
        assertTrue(report.isReconciled());
        assertEquals(1, report.getLines().size());
    }

    @Test
    void getReconciliationReportsVarianceRatherThanHidingIt() {
        when(inventoryTotalRepository.findAllWithShopAndProduct()).thenReturn(List.of(total(100, 0)));
        when(inventoryValuationService.getTotalInventoryValue()).thenReturn(new BigDecimal("1000.00"));
        when(controlAccountReconciliationService.getInventoryAssetGlBalance(LocalDate.of(2026, 1, 1)))
                .thenReturn(new BigDecimal("800.00"));
        when(inventoryValuationService.getInventoryValueDetailed(shop, product))
                .thenReturn(InventoryValuationService.ValuationResult.builder()
                        .totalValue(new BigDecimal("1000.00")).valuedQuantity(100).unvaluedQuantity(0).build());
        when(inventoryValuationService.getUnitCost(shop, product)).thenReturn(Optional.of(new BigDecimal("10.00")));

        InventoryReconciliationReport report = service.getReconciliation(LocalDate.of(2026, 1, 1));

        assertFalse(report.isReconciled());
        assertEquals(0, new BigDecimal("200.00").compareTo(report.getVariance()));
    }
}
