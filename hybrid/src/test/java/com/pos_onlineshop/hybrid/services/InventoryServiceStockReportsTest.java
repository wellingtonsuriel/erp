package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.dtos.ShopStockReport;
import com.pos_onlineshop.hybrid.dtos.StockSummaryReport;
import com.pos_onlineshop.hybrid.dtos.StockValueReport;
import com.pos_onlineshop.hybrid.inventory.InventoryItemRepository;
import com.pos_onlineshop.hybrid.inventoryTotal.InventoryTotal;
import com.pos_onlineshop.hybrid.inventoryTotal.InventoryTotalRepository;
import com.pos_onlineshop.hybrid.inventoryTransfer.InventoryTransferRepository;
import com.pos_onlineshop.hybrid.products.Product;
import com.pos_onlineshop.hybrid.shop.Shop;
import com.pos_onlineshop.hybrid.shop.ShopRepository;
import com.pos_onlineshop.hybrid.shopInventory.ShopInventory;
import com.pos_onlineshop.hybrid.shopInventory.ShopInventoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Guards against the "latest received lot's price applied to the whole on-hand quantity"
 * approximation creeping back into these reports - see INVENTORY_VALUATION.md. Before this fix,
 * generateStockSummaryReport/generateShopStockReport/generateStockValueReport priced every
 * on-hand unit at ShopInventory.findFirstByShopAndProductOrderByIdDesc's price, disagreeing with
 * InventoryValuationService/InventoryReportService whenever a product had multiple cost layers.
 */
@ExtendWith(MockitoExtension.class)
class InventoryServiceStockReportsTest {

    @Mock private InventoryItemRepository inventoryRepository;
    @Mock private InventoryTotalRepository inventoryTotalRepository;
    @Mock private InventoryTransferRepository transferRepository;
    @Mock private ShopRepository shopRepository;
    @Mock private ShopInventoryRepository shopInventoryRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private InventoryValuationService inventoryValuationService;

    private InventoryService service;

    private Shop shop;
    private Product product;

    @BeforeEach
    void setUp() {
        service = new InventoryService(inventoryRepository, inventoryTotalRepository, transferRepository,
                shopRepository, shopInventoryRepository, messagingTemplate, inventoryValuationService);
        shop = Shop.builder().id(1L).code("SHOP-001").name("Main Shop").build();
        product = Product.builder().id(1L).name("Widget").category("General").build();
    }

    private InventoryTotal total(int stock) {
        return InventoryTotal.builder().shop(shop).product(product).totalstock(stock).build();
    }

    @Test
    void stockSummaryReportUsesRealFifoValueNotTheLatestLotPrice() {
        // Two lots at different costs: FIFO value (60@10 + 40@12 = 1080) must NOT equal
        // "latest lot price ($12) x total stock (100)" = $1200, the old buggy approximation.
        when(inventoryTotalRepository.findAllWithShopAndProduct()).thenReturn(List.of(total(100)));
        when(shopRepository.findByActiveTrue()).thenReturn(List.of(shop));
        when(inventoryTotalRepository.findOutOfStockItems()).thenReturn(List.of());
        when(inventoryValuationService.getInventoryValue(shop, product))
                .thenReturn(new BigDecimal("1080.00"));

        StockSummaryReport report = service.generateStockSummaryReport();

        assertEquals(0, new BigDecimal("1080.00").compareTo(report.getTotalStockValue()));
        assertEquals(0, new BigDecimal("1080.00").compareTo(report.getShopBreakdown().get(0).getTotalStockValue()));
    }

    @Test
    void shopStockReportUsesRealFifoValueAndFrontOfQueueUnitCost() {
        when(shopRepository.findById(1L)).thenReturn(Optional.of(shop));
        when(inventoryTotalRepository.findByShopIdWithDetails(1L)).thenReturn(List.of(total(100)));
        lenient().when(shopInventoryRepository.findFirstByShopAndProductOrderByIdDesc(shop, product))
                .thenReturn(Optional.of(ShopInventory.builder().reorderLevel(20).build()));
        when(inventoryValuationService.getInventoryValue(shop, product)).thenReturn(new BigDecimal("1080.00"));
        when(inventoryValuationService.getUnitCost(shop, product)).thenReturn(Optional.of(new BigDecimal("10.00")));

        ShopStockReport report = service.generateShopStockReport(1L);

        assertEquals(0, new BigDecimal("1080.00").compareTo(report.getTotalStockValue()));
        ShopStockReport.ProductStockDetail line = report.getProducts().get(0);
        assertEquals(0, new BigDecimal("1080.00").compareTo(line.getTotalValue()));
        assertEquals(0, new BigDecimal("10.00").compareTo(line.getUnitPrice()));
    }

    @Test
    void stockValueReportUsesRealFifoValueNotTheLatestLotPrice() {
        when(inventoryTotalRepository.findAllWithShopAndProduct()).thenReturn(List.of(total(100)));
        when(inventoryValuationService.getInventoryValue(shop, product)).thenReturn(new BigDecimal("1080.00"));

        StockValueReport report = service.generateStockValueReport();

        assertEquals(0, new BigDecimal("1080.00").compareTo(report.getTotalInventoryValue()));
        assertEquals(0, new BigDecimal("1080.00").compareTo(report.getShopValues().get(0).getTotalValue()));
        assertEquals(0, new BigDecimal("1080.00").compareTo(report.getCategoryValues().get(0).getTotalValue()));
    }
}
