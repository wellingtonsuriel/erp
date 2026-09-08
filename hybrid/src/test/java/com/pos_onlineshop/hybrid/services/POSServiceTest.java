package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.cashier.Cashier;
import com.pos_onlineshop.hybrid.cashierSessions.CashierSession;
import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.dtos.QuickSaleItem;
import com.pos_onlineshop.hybrid.enums.CashierRole;
import com.pos_onlineshop.hybrid.enums.PaymentMethod;
import com.pos_onlineshop.hybrid.enums.Permission;
import com.pos_onlineshop.hybrid.gl.FinancialEvent;
import com.pos_onlineshop.hybrid.journalEntry.JournalEntry;
import com.pos_onlineshop.hybrid.orders.Order;
import com.pos_onlineshop.hybrid.orders.OrderRepository;
import com.pos_onlineshop.hybrid.products.Product;
import com.pos_onlineshop.hybrid.selling_price.SellingPrice;
import com.pos_onlineshop.hybrid.shop.Shop;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * POSService previously had zero test coverage despite being the module that processes real
 * financial transactions (the audit's own finding). Covers the two contract fixes in this
 * change: a price override is only honored when the acting cashier actually holds
 * Permission.OVERRIDE_PRICE (never applied just because a customPrice field was present), and
 * every payment method the POS terminal can send is accepted and posted correctly. Does not
 * attempt full end-to-end coverage of every POSService method - see the audit's disclosed gap
 * list for what remains untested (barcode scan, daily summary, receipt generation).
 */
@ExtendWith(MockitoExtension.class)
class POSServiceTest {

    @Mock private OrderService orderService;
    @Mock private ProductService productService;
    @Mock private OrderRepository orderRepository;
    @Mock private ShopInventoryService shopInventoryService;
    @Mock private AccountancyService accountancyService;
    @Mock private SellingPriceService sellingPriceService;
    @Mock private GLPostingService glPostingService;
    @Mock private CurrencyService currencyService;
    @Mock private com.pos_onlineshop.hybrid.journalEntry.JournalEntryRepository journalEntryRepository;
    @Mock private InventoryValuationService inventoryValuationService;
    @Mock private CashierService cashierService;

    private POSService service;

    private Shop shop;
    private Product product;
    private Currency currency;
    private Cashier cashier;
    private CashierSession session;

    @BeforeEach
    void setUp() {
        service = new POSService(orderService, productService, orderRepository, shopInventoryService,
                accountancyService, sellingPriceService, glPostingService, currencyService,
                journalEntryRepository, inventoryValuationService, cashierService);

        currency = Currency.builder().id(1L).code("USD").build();
        shop = Shop.builder().id(1L).code("SHOP-001").name("Main Shop").defaultCurrency(currency).build();
        product = Product.builder().id(1L).name("Widget").category("General").sku("SKU-1").build();
        cashier = Cashier.builder().id(1L).employeeId("EMP1").username("cashier1").firstName("A").lastName("B")
                .email("a@b.com").role(CashierRole.CASHIER).active(true).build();
        session = CashierSession.builder().id(1L).cashier(cashier).shop(shop).build();

        lenient().when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order order = inv.getArgument(0);
            order.setId(100L);
            return order;
        });
        lenient().when(currencyService.getBaseCurrency()).thenReturn(currency);
        lenient().when(inventoryValuationService.getCostForSale(any(), any(), anyInt(), anyString()))
                .thenReturn(InventoryValuationService.CostResult.builder()
                        .totalCost(BigDecimal.ZERO).quantityCosted(0).quantityRequested(0).fullyCosted(false).build());
        lenient().when(glPostingService.post(any(FinancialEvent.class)))
                .thenReturn(JournalEntry.builder().id(1L).entryNumber(1L).build());
    }

    private QuickSaleItem item(Long productId, int quantity, BigDecimal customPrice) {
        QuickSaleItem item = new QuickSaleItem();
        item.setProductId(productId);
        item.setQuantity(quantity);
        item.setCustomPrice(customPrice);
        return item;
    }

    private SellingPrice catalogPrice(BigDecimal price) {
        return SellingPrice.builder().id(1L).product(product).shop(shop).currency(currency)
                .sellingPrice(price).build();
    }

    @Test
    void cashSaleAtTheCatalogPriceSucceeds() {
        when(productService.findById(1L)).thenReturn(Optional.of(product));
        when(shopInventoryService.isInStock(1L, 1L, 2)).thenReturn(true);
        when(sellingPriceService.getCurrentPrice(product, shop)).thenReturn(Optional.of(catalogPrice(new BigDecimal("10.00"))));

        Order order = service.processQuickSale(List.of(item(1L, 2, null)), PaymentMethod.CASH,
                new BigDecimal("20.00"), session);

        assertEquals(1, order.getOrderLines().size());
        assertEquals(0, new BigDecimal("10.00").compareTo(order.getOrderLines().get(0).getUnitPrice()));
        verify(shopInventoryService).reduceStock(1L, 1L, 2);
        verify(glPostingService).post(any(FinancialEvent.class));
    }

    @Test
    void eachNonCashPaymentMethodIsAcceptedAndPosted() {
        when(productService.findById(1L)).thenReturn(Optional.of(product));
        when(shopInventoryService.isInStock(1L, 1L, 1)).thenReturn(true);
        when(sellingPriceService.getCurrentPrice(product, shop)).thenReturn(Optional.of(catalogPrice(new BigDecimal("5.00"))));

        for (PaymentMethod method : PaymentMethod.values()) {
            Order order = service.processQuickSale(List.of(item(1L, 1, null)), method, null, session);
            assertEquals(method, order.getPaymentMethod());
        }

        verify(glPostingService, times(PaymentMethod.values().length)).post(any(FinancialEvent.class));
    }

    @Test
    void priceOverrideIsAppliedWhenTheCashierHasThePermission() {
        Cashier manager = Cashier.builder().id(2L).employeeId("EMP2").username("manager1").firstName("M").lastName("B")
                .email("m@b.com").role(CashierRole.MANAGER).active(true).build();
        CashierSession managerSession = CashierSession.builder().id(2L).cashier(manager).shop(shop).build();

        when(productService.findById(1L)).thenReturn(Optional.of(product));
        when(shopInventoryService.isInStock(1L, 1L, 1)).thenReturn(true);
        when(sellingPriceService.getCurrentPrice(product, shop)).thenReturn(Optional.of(catalogPrice(new BigDecimal("10.00"))));
        when(cashierService.hasPermission(manager, Permission.OVERRIDE_PRICE)).thenReturn(true);

        Order order = service.processQuickSale(List.of(item(1L, 1, new BigDecimal("6.00"))), PaymentMethod.CASH,
                new BigDecimal("6.00"), managerSession);

        assertEquals(0, new BigDecimal("6.00").compareTo(order.getOrderLines().get(0).getUnitPrice()));
    }

    @Test
    void priceOverrideIsRejectedWhenTheCashierLacksThePermission() {
        when(productService.findById(1L)).thenReturn(Optional.of(product));
        when(shopInventoryService.isInStock(1L, 1L, 1)).thenReturn(true);
        when(sellingPriceService.getCurrentPrice(product, shop)).thenReturn(Optional.of(catalogPrice(new BigDecimal("10.00"))));
        when(cashierService.hasPermission(cashier, Permission.OVERRIDE_PRICE)).thenReturn(false);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.processQuickSale(
                List.of(item(1L, 1, new BigDecimal("1.00"))), PaymentMethod.CASH, null, session));

        assertTrue(ex.getMessage().contains("not authorized to override prices"));
        verify(shopInventoryService, never()).reduceStock(any(), any(), any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void priceOverrideRejectsAZeroOrNegativeCustomPriceEvenWithPermission() {
        when(productService.findById(1L)).thenReturn(Optional.of(product));
        when(shopInventoryService.isInStock(1L, 1L, 1)).thenReturn(true);
        when(sellingPriceService.getCurrentPrice(product, shop)).thenReturn(Optional.of(catalogPrice(new BigDecimal("10.00"))));
        when(cashierService.hasPermission(cashier, Permission.OVERRIDE_PRICE)).thenReturn(true);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.processQuickSale(
                List.of(item(1L, 1, BigDecimal.ZERO)), PaymentMethod.CASH, null, session));

        assertTrue(ex.getMessage().contains("Custom price must be positive"));
    }

    @Test
    void saleFailsWhenStockIsInsufficient() {
        when(productService.findById(1L)).thenReturn(Optional.of(product));
        when(shopInventoryService.isInStock(1L, 1L, 5)).thenReturn(false);

        assertThrows(RuntimeException.class, () -> service.processQuickSale(
                List.of(item(1L, 5, null)), PaymentMethod.CASH, null, session));

        verify(orderRepository, never()).save(any());
        verify(glPostingService, never()).post(any());
    }

    @Test
    void saleFailsAndNeverPostsToGlWhenReduceStockRejectsReservedStock() {
        // P0-3: even though isInStock passed, the authoritative lock-guarded check inside
        // reduceStock can still reject (a concurrent reservation won the race) - the sale must
        // fail cleanly rather than post a GL entry for stock that was never actually removed.
        when(productService.findById(1L)).thenReturn(Optional.of(product));
        when(shopInventoryService.isInStock(1L, 1L, 1)).thenReturn(true);
        when(sellingPriceService.getCurrentPrice(product, shop)).thenReturn(Optional.of(catalogPrice(new BigDecimal("10.00"))));
        doThrow(new RuntimeException("Insufficient available stock. Available: 0, Requested: 1"))
                .when(shopInventoryService).reduceStock(1L, 1L, 1);

        assertThrows(RuntimeException.class, () -> service.processQuickSale(
                List.of(item(1L, 1, null)), PaymentMethod.CASH, null, session));

        verify(glPostingService, never()).post(any());
    }
}
