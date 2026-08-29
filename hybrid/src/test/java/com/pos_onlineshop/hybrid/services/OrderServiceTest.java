package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.cart.Cart;
import com.pos_onlineshop.hybrid.cartItem.CartItem;
import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.enums.OrderStatus;
import com.pos_onlineshop.hybrid.enums.SalesChannel;
import com.pos_onlineshop.hybrid.enums.ShopType;
import com.pos_onlineshop.hybrid.mappers.OrderMapper;
import com.pos_onlineshop.hybrid.orderLines.OrderLine;
import com.pos_onlineshop.hybrid.orderLines.OrderLineRepository;
import com.pos_onlineshop.hybrid.orders.Order;
import com.pos_onlineshop.hybrid.orders.OrderRepository;
import com.pos_onlineshop.hybrid.products.Product;
import com.pos_onlineshop.hybrid.selling_price.SellingPrice;
import com.pos_onlineshop.hybrid.shop.Shop;
import com.pos_onlineshop.hybrid.shop.ShopRepository;
import com.pos_onlineshop.hybrid.userAccount.UserAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderLineRepository orderLineRepository;
    @Mock private CartService cartService;
    @Mock private InventoryService inventoryService;
    @Mock private AccountancyService accountancyService;
    @Mock private CurrencyService currencyService;
    @Mock private ProductService productService;
    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private com.pos_onlineshop.hybrid.customers.CustomersRepository customersRepository;
    @Mock private OrderMapper orderMapper;
    @Mock private ShopInventoryService shopInventoryService;
    @Mock private ShopRepository shopRepository;
    @Mock private ZimraService zimraService;
    @Mock private SellingPriceService sellingPriceService;
    @Mock private GLPostingService glPostingService;

    private OrderService service;

    private UserAccount user;
    private Currency currency;
    private Product product;
    private Shop onlineShop;
    private Shop retailShop;

    @BeforeEach
    void setUp() {
        service = new OrderService(orderRepository, orderLineRepository, cartService, inventoryService,
                accountancyService, currencyService, productService, messagingTemplate, customersRepository,
                orderMapper, shopInventoryService, shopRepository, zimraService, sellingPriceService, glPostingService);

        user = UserAccount.builder().id(1L).username("customer1").password("x").email("c1@test.com").build();
        currency = Currency.builder().id(1L).code("USD").build();
        product = Product.builder().id(1L).name("Widget").build();
        onlineShop = Shop.builder().id(10L).code("ONLINE-1").name("Online Store").type(ShopType.ONLINE).active(true).build();
        retailShop = Shop.builder().id(20L).code("SHOP-001").name("Retail Shop").type(ShopType.RETAIL).active(true).build();

        lenient().when(currencyService.getBaseCurrency()).thenReturn(currency);
    }

    private Cart cartWith(int quantity) {
        CartItem item = CartItem.builder().id(1L).product(product).quantity(quantity).build();
        return Cart.builder().id(1L).user(user).cartItems(List.of(item)).build();
    }

    private SellingPrice sellingPrice() {
        return SellingPrice.builder().id(1L).product(product).currency(currency)
                .sellingPrice(new BigDecimal("15.00")).basePrice(new BigDecimal("15.00")).active(true).build();
    }

    @Test
    void createOrderFromCartPicksTheConfiguredDefaultOnlineShopAndReservesStock() {
        when(cartService.getCartByUser(user)).thenReturn(Optional.of(cartWith(3)));
        when(shopRepository.findByActiveTrueAndType(ShopType.ONLINE)).thenReturn(List.of(onlineShop));
        when(shopInventoryService.isInStock(10L, 1L, 3)).thenReturn(true);
        when(sellingPriceService.getProductPrices(product)).thenReturn(List.of(sellingPrice()));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order result = service.createOrderFromCart(user, "123 Street",
                com.pos_onlineshop.hybrid.enums.PaymentMethod.ONLINE_PAYMENT, SalesChannel.ONLINE, currency);

        assertEquals(onlineShop, result.getShop());
        verify(shopInventoryService).reserveStock(10L, 1L, 3);
        verify(shopInventoryService, never()).reduceStock(anyLong(), anyLong(), anyInt());
        verify(shopRepository, never()).findByActiveTrue();
    }

    @Test
    void createOrderFromCartFallsBackToAnyShopWithSufficientStockWhenNoOnlineShopConfigured() {
        when(cartService.getCartByUser(user)).thenReturn(Optional.of(cartWith(2)));
        when(shopRepository.findByActiveTrueAndType(ShopType.ONLINE)).thenReturn(List.of());
        when(shopRepository.findByActiveTrue()).thenReturn(List.of(retailShop));
        when(shopInventoryService.isInStock(20L, 1L, 2)).thenReturn(true);
        when(sellingPriceService.getProductPrices(product)).thenReturn(List.of(sellingPrice()));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order result = service.createOrderFromCart(user, "123 Street",
                com.pos_onlineshop.hybrid.enums.PaymentMethod.ONLINE_PAYMENT, SalesChannel.ONLINE, currency);

        assertEquals(retailShop, result.getShop());
        verify(shopInventoryService).reserveStock(20L, 1L, 2);
    }

    @Test
    void createOrderFromCartThrowsWhenNoShopCanFulfillIt() {
        when(cartService.getCartByUser(user)).thenReturn(Optional.of(cartWith(50)));
        when(shopRepository.findByActiveTrueAndType(ShopType.ONLINE)).thenReturn(List.of());
        when(shopRepository.findByActiveTrue()).thenReturn(List.of(retailShop));
        when(shopInventoryService.isInStock(20L, 1L, 50)).thenReturn(false);

        assertThrows(RuntimeException.class, () -> service.createOrderFromCart(user, "123 Street",
                com.pos_onlineshop.hybrid.enums.PaymentMethod.ONLINE_PAYMENT, SalesChannel.ONLINE, currency));
        verify(shopInventoryService, never()).reserveStock(anyLong(), anyLong(), anyInt());
    }

    @Test
    void createOrderFromCartReducesStockDirectlyForNonOnlineChannel() {
        when(cartService.getCartByUser(user)).thenReturn(Optional.of(cartWith(2)));
        when(shopRepository.findByActiveTrueAndType(ShopType.ONLINE)).thenReturn(List.of(onlineShop));
        when(shopInventoryService.isInStock(10L, 1L, 2)).thenReturn(true);
        when(sellingPriceService.getProductPrices(product)).thenReturn(List.of(sellingPrice()));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        service.createOrderFromCart(user, "123 Street",
                com.pos_onlineshop.hybrid.enums.PaymentMethod.CASH, SalesChannel.POS, currency);

        verify(shopInventoryService).reduceStock(10L, 1L, 2);
        verify(shopInventoryService, never()).reserveStock(anyLong(), anyLong(), anyInt());
    }

    private Order pendingOnlineOrder() {
        OrderLine line = OrderLine.builder().id(1L).product(product).quantity(5).build();
        Order order = Order.builder().id(100L).status(OrderStatus.PENDING).salesChannel(SalesChannel.ONLINE)
                .shop(onlineShop).currency(currency).totalAmount(BigDecimal.TEN).build();
        order.addOrderLine(line);
        return order;
    }

    @Test
    void confirmingAPendingOnlineOrderCommitsTheReservation() {
        Order order = pendingOnlineOrder();
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        service.updateOrderStatus(100L, OrderStatus.CONFIRMED);

        verify(shopInventoryService).commitReservedStock(10L, 1L, 5);
    }

    @Test
    void confirmingAnAlreadyConfirmedOrderDoesNotCommitAgain() {
        Order order = pendingOnlineOrder();
        order.setStatus(OrderStatus.CONFIRMED); // already confirmed - oldStatus will be CONFIRMED, not PENDING
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        service.updateOrderStatus(100L, OrderStatus.CONFIRMED);

        verify(shopInventoryService, never()).commitReservedStock(anyLong(), anyLong(), anyInt());
    }

    @Test
    void cancellingAPendingOnlineOrderReleasesTheReservationRatherThanRestockingIt() {
        Order order = pendingOnlineOrder();
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        service.updateOrderStatus(100L, OrderStatus.CANCELLED);

        verify(shopInventoryService).releaseReservation(10L, 1L, 5);
        verify(shopInventoryService, never()).addStock(anyLong(), anyLong(), anyInt());
    }

    @Test
    void cancellingAConfirmedOnlineOrderRestocksPhysicalInventory() {
        Order order = pendingOnlineOrder();
        order.setStatus(OrderStatus.CONFIRMED); // was already confirmed - stock was committed, not just reserved
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        service.updateOrderStatus(100L, OrderStatus.CANCELLED);

        verify(shopInventoryService).addStock(10L, 1L, 5);
        verify(shopInventoryService, never()).releaseReservation(anyLong(), anyLong(), anyInt());
    }

    @Test
    void deletingALegacyOrderWithNoShopFallsBackToTheDeprecatedPool() {
        OrderLine line = OrderLine.builder().id(1L).product(product).quantity(5).build();
        Order legacyOrder = Order.builder().id(200L).status(OrderStatus.PENDING)
                .salesChannel(SalesChannel.ONLINE).shop(null).currency(currency).build();
        legacyOrder.addOrderLine(line);
        when(orderRepository.findById(200L)).thenReturn(Optional.of(legacyOrder));

        service.deleteOrder(200L);

        verify(inventoryService).addStock(1L, 5);
        verifyNoInteractions(shopInventoryService);
        verify(orderRepository).deleteById(200L);
    }
}
