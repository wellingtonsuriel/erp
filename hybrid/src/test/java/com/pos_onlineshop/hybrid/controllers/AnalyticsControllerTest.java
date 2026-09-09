package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.dtos.ProfitAndLossReport;
import com.pos_onlineshop.hybrid.services.*;
import com.pos_onlineshop.hybrid.shop.Shop;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

/**
 * The audit's P1-7 finding: /api/analytics/performance hardcoded inventoryTurnover,
 * customerRetentionRate and averageOrderProcessingTime to 0.0 regardless of real data, and
 * shop-specific revenue/order-count were TODO placeholders. These tests verify the replacement
 * calculations actually derive from real service data rather than fabricating a number, and
 * that the metric with no honest way to compute it (averageOrderProcessingTime) is omitted
 * rather than faked.
 */
@ExtendWith(MockitoExtension.class)
class AnalyticsControllerTest {

    @Mock private OrderService orderService;
    @Mock private UserAccountService userAccountService;
    @Mock private AccountancyService accountancyService;
    @Mock private ShopInventoryService shopInventoryService;
    @Mock private CurrencyService currencyService;
    @Mock private ProfitAndLossService profitAndLossService;
    @Mock private InventoryValuationService inventoryValuationService;
    @Mock private ShopService shopService;

    private AnalyticsController controller;
    private Currency usd;

    @BeforeEach
    void setUp() {
        controller = new AnalyticsController(orderService, userAccountService, accountancyService,
                shopInventoryService, currencyService, profitAndLossService, inventoryValuationService, shopService);
        usd = Currency.builder().id(1L).code("USD").symbol("$").name("US Dollar").build();
        when(currencyService.findByCode("USD")).thenReturn(Optional.of(usd));
    }

    @Test
    void dashboardFailureReturnsAGenericErrorNeverTheRawExceptionMessage() {
        when(orderService.countRecentOrders(1))
                .thenThrow(new RuntimeException("Duplicate entry 'admin@internal.local' for key users.email_UNIQUE"));

        ResponseEntity<Map<String, Object>> response = controller.getDashboardData("USD", null);

        assertEquals(500, response.getStatusCode().value());
        String error = (String) response.getBody().get("error");
        assertFalse(error.contains("Duplicate entry"), "must never leak the raw exception message to the client");
        assertFalse(error.contains("users.email_UNIQUE"), "must never leak internal schema details to the client");
    }

    @Test
    void performanceMetricsNeverIncludesTheUnimplementableProcessingTimeMetric() {
        stubBaselineOrderCounts();
        when(profitAndLossService.generate(any(LocalDate.class), any(LocalDate.class), isNull())).thenReturn(
                ProfitAndLossReport.builder().totalCostOfGoodsSold(BigDecimal.ZERO).build());
        when(inventoryValuationService.getTotalInventoryValue()).thenReturn(BigDecimal.ZERO);
        when(orderService.findDistinctCustomerIdsBetween(any(), any())).thenReturn(List.of());

        ResponseEntity<Map<String, Object>> response = controller.getPerformanceMetrics("USD", null);

        assertFalse(response.getBody().containsKey("averageOrderProcessingTime"),
                "must never report a fabricated processing-time metric");
    }

    @Test
    void inventoryTurnoverIsCostOfGoodsSoldOverTotalInventoryValueSystemWide() {
        stubBaselineOrderCounts();
        when(profitAndLossService.generate(any(LocalDate.class), any(LocalDate.class), isNull()))
                .thenReturn(ProfitAndLossReport.builder().totalCostOfGoodsSold(new BigDecimal("500")).build());
        when(inventoryValuationService.getTotalInventoryValue()).thenReturn(new BigDecimal("250"));
        when(orderService.findDistinctCustomerIdsBetween(any(), any())).thenReturn(List.of());

        ResponseEntity<Map<String, Object>> response = controller.getPerformanceMetrics("USD", null);

        assertEquals(2.0, (double) response.getBody().get("inventoryTurnover"), 0.0001);
    }

    @Test
    void inventoryTurnoverIsZeroWithoutFailingWhenThereIsNoInventoryValueToDivideBy() {
        stubBaselineOrderCounts();
        when(profitAndLossService.generate(any(LocalDate.class), any(LocalDate.class), isNull()))
                .thenReturn(ProfitAndLossReport.builder().totalCostOfGoodsSold(new BigDecimal("500")).build());
        when(inventoryValuationService.getTotalInventoryValue()).thenReturn(BigDecimal.ZERO);
        when(orderService.findDistinctCustomerIdsBetween(any(), any())).thenReturn(List.of());

        ResponseEntity<Map<String, Object>> response = controller.getPerformanceMetrics("USD", null);

        assertEquals(0.0, (double) response.getBody().get("inventoryTurnover"), 0.0001);
    }

    @Test
    void inventoryTurnoverIsShopScopedWhenAShopIdIsGiven() {
        stubBaselineOrderCounts();
        Shop shop = Shop.builder().id(7L).build();
        when(shopService.findById(7L)).thenReturn(Optional.of(shop));
        when(profitAndLossService.generate(any(LocalDate.class), any(LocalDate.class), eq(7L)))
                .thenReturn(ProfitAndLossReport.builder().totalCostOfGoodsSold(new BigDecimal("100")).build());
        when(inventoryValuationService.getInventoryValue(shop)).thenReturn(new BigDecimal("50"));
        when(orderService.findDistinctCustomerIdsBetween(any(), any())).thenReturn(List.of());
        when(orderService.calculateShopRevenue(7L, usd)).thenReturn(BigDecimal.ZERO);
        when(orderService.countShopOrders(7L)).thenReturn(0L);

        ResponseEntity<Map<String, Object>> response = controller.getPerformanceMetrics("USD", 7L);

        assertEquals(2.0, (double) response.getBody().get("inventoryTurnover"), 0.0001);
    }

    @Test
    void customerRetentionRateIsTheShareOfPreviousPeriodCustomersWhoReturned() {
        stubBaselineOrderCounts();
        when(profitAndLossService.generate(any(LocalDate.class), any(LocalDate.class), isNull())).thenReturn(
                ProfitAndLossReport.builder().totalCostOfGoodsSold(BigDecimal.ZERO).build());
        when(inventoryValuationService.getTotalInventoryValue()).thenReturn(BigDecimal.ZERO);
        // previous window: customers 1,2,3,4 ; current window: 2,3,5 -> 2 of 4 retained = 50%
        when(orderService.findDistinctCustomerIdsBetween(any(), any()))
                .thenReturn(List.of(1L, 2L, 3L, 4L))
                .thenReturn(List.of(2L, 3L, 5L));

        ResponseEntity<Map<String, Object>> response = controller.getPerformanceMetrics("USD", null);

        assertEquals(50.0, (double) response.getBody().get("customerRetentionRate"), 0.0001);
    }

    @Test
    void customerRetentionRateIsZeroWithoutDivideByZeroWhenThereWasNoPriorPeriod() {
        stubBaselineOrderCounts();
        when(profitAndLossService.generate(any(LocalDate.class), any(LocalDate.class), isNull())).thenReturn(
                ProfitAndLossReport.builder().totalCostOfGoodsSold(BigDecimal.ZERO).build());
        when(inventoryValuationService.getTotalInventoryValue()).thenReturn(BigDecimal.ZERO);
        when(orderService.findDistinctCustomerIdsBetween(any(), any())).thenReturn(List.of());

        ResponseEntity<Map<String, Object>> response = controller.getPerformanceMetrics("USD", null);

        assertEquals(0.0, (double) response.getBody().get("customerRetentionRate"), 0.0001);
    }

    @Test
    void shopPerformanceUsesTheRealShopScopedRevenueAndOrderCount() {
        stubBaselineOrderCounts();
        Shop shop = Shop.builder().id(7L).build();
        when(shopService.findById(7L)).thenReturn(Optional.of(shop));
        when(profitAndLossService.generate(any(LocalDate.class), any(LocalDate.class), eq(7L)))
                .thenReturn(ProfitAndLossReport.builder().totalCostOfGoodsSold(BigDecimal.ZERO).build());
        when(inventoryValuationService.getInventoryValue(shop)).thenReturn(BigDecimal.ZERO);
        when(orderService.findDistinctCustomerIdsBetween(any(), any())).thenReturn(List.of());
        when(orderService.calculateShopRevenue(7L, usd)).thenReturn(new BigDecimal("777"));
        when(orderService.countShopOrders(7L)).thenReturn(3L);

        ResponseEntity<Map<String, Object>> response = controller.getPerformanceMetrics("USD", 7L);

        @SuppressWarnings("unchecked")
        Map<String, Object> shopPerformance = (Map<String, Object>) response.getBody().get("shopPerformance");
        assertEquals(0, new BigDecimal("777").compareTo((BigDecimal) shopPerformance.get("shopRevenue")));
        assertEquals(3L, shopPerformance.get("shopOrderCount"));
    }

    private void stubBaselineOrderCounts() {
        when(orderService.calculateRevenue(any(), any())).thenReturn(BigDecimal.ZERO);
        when(orderService.countRecentOrders(anyInt())).thenReturn(0L);
        when(orderService.countOrdersByStatus(any())).thenReturn(0L);
    }
}
