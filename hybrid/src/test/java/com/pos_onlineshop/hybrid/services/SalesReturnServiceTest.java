package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.account.Account;
import com.pos_onlineshop.hybrid.account.AccountRepository;
import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.dtos.CreateSalesReturnRequest;
import com.pos_onlineshop.hybrid.dtos.SalesReturnLineRequest;
import com.pos_onlineshop.hybrid.dtos.SalesReturnResponse;
import com.pos_onlineshop.hybrid.enums.*;
import com.pos_onlineshop.hybrid.gl.ManualLineSpec;
import com.pos_onlineshop.hybrid.inventoryTotal.InventoryTotal;
import com.pos_onlineshop.hybrid.inventoryTotal.InventoryTotalRepository;
import com.pos_onlineshop.hybrid.journalEntry.JournalEntry;
import com.pos_onlineshop.hybrid.orderLines.OrderLine;
import com.pos_onlineshop.hybrid.orderLines.OrderLineRepository;
import com.pos_onlineshop.hybrid.orders.Order;
import com.pos_onlineshop.hybrid.orders.OrderRepository;
import com.pos_onlineshop.hybrid.products.Product;
import com.pos_onlineshop.hybrid.salesReturn.SalesReturn;
import com.pos_onlineshop.hybrid.salesReturn.SalesReturnLine;
import com.pos_onlineshop.hybrid.salesReturn.SalesReturnLineRepository;
import com.pos_onlineshop.hybrid.salesReturn.SalesReturnRepository;
import com.pos_onlineshop.hybrid.shop.Shop;
import com.pos_onlineshop.hybrid.userAccount.UserAccount;
import com.pos_onlineshop.hybrid.userAccount.UserAccountRepository;
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
class SalesReturnServiceTest {

    @Mock private SalesReturnRepository salesReturnRepository;
    @Mock private SalesReturnLineRepository salesReturnLineRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private OrderLineRepository orderLineRepository;
    @Mock private UserAccountRepository userAccountRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private ShopInventoryService shopInventoryService;
    @Mock private GLPostingService glPostingService;

    private SalesReturnService service;
    private Currency usd;
    private UserAccount clerk;
    private Shop shop;
    private Product product;
    private Order order;
    private OrderLine orderLine;
    private Account salesReturns;
    private Account vatOutput;
    private Account cash;
    private Account inventory;
    private Account cogs;

    @BeforeEach
    void setUp() {
        service = new SalesReturnService(salesReturnRepository, salesReturnLineRepository, orderRepository,
                orderLineRepository, userAccountRepository, accountRepository, shopInventoryService, glPostingService);

        usd = Currency.builder().id(1L).code("USD").build();
        clerk = UserAccount.builder().id(1L).username("clerk1").password("x").email("clerk1@test.com").build();
        shop = Shop.builder().id(1L).name("Main Shop").build();
        product = Product.builder().id(1L).name("Widget").build();
        order = Order.builder().id(100L).shop(shop).currency(usd).status(OrderStatus.COMPLETED)
                .salesChannel(SalesChannel.POS).paymentMethod(PaymentMethod.CASH).build();
        orderLine = OrderLine.builder().id(200L).order(order).product(product).productName("Widget")
                .quantity(4).unitPrice(new BigDecimal("10.00")).taxAmount(new BigDecimal("4.00")).unitCost(new BigDecimal("6.00")).build();

        salesReturns = Account.builder().id(1L).code("4900").name("Sales Returns & Allowances")
                .accountType(AccountType.REVENUE).normalBalance(DebitCredit.DEBIT).active(true).build();
        vatOutput = Account.builder().id(2L).code("2200").name("VAT Output / Payable")
                .accountType(AccountType.LIABILITY).normalBalance(DebitCredit.CREDIT).active(true).build();
        cash = Account.builder().id(3L).code("1010").name("Cash on Hand")
                .accountType(AccountType.ASSET).normalBalance(DebitCredit.DEBIT).active(true).build();
        inventory = Account.builder().id(4L).code("1200").name("Inventory Asset")
                .accountType(AccountType.ASSET).normalBalance(DebitCredit.DEBIT).active(true).build();
        cogs = Account.builder().id(5L).code("5000").name("Cost of Goods Sold")
                .accountType(AccountType.EXPENSE).normalBalance(DebitCredit.DEBIT).active(true).build();

        lenient().when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        lenient().when(orderLineRepository.findById(200L)).thenReturn(Optional.of(orderLine));
        lenient().when(userAccountRepository.findById(1L)).thenReturn(Optional.of(clerk));
        lenient().when(accountRepository.findByCode("4900")).thenReturn(Optional.of(salesReturns));
        lenient().when(accountRepository.findByCode("2200")).thenReturn(Optional.of(vatOutput));
        lenient().when(accountRepository.findByCode("1010")).thenReturn(Optional.of(cash));
        lenient().when(accountRepository.findByCode("1200")).thenReturn(Optional.of(inventory));
        lenient().when(accountRepository.findByCode("5000")).thenReturn(Optional.of(cogs));
        lenient().when(salesReturnLineRepository.sumQuantityReturnedByOrderLineId(200L)).thenReturn(0);
        lenient().when(salesReturnRepository.save(any(SalesReturn.class))).thenAnswer(inv -> {
            SalesReturn r = inv.getArgument(0);
            if (r.getId() == null) r.setId(300L);
            return r;
        });
    }

    private CreateSalesReturnRequest request(int quantity) {
        CreateSalesReturnRequest request = new CreateSalesReturnRequest();
        request.setReturnNumber("RET-1");
        request.setOrderId(100L);
        request.setReason("Damaged item");
        request.setReturnDate(LocalDate.of(2026, 8, 15));
        request.setCreatedByUserId(1L);
        SalesReturnLineRequest line = new SalesReturnLineRequest();
        line.setOrderLineId(200L);
        line.setQuantityReturned(quantity);
        request.setLines(List.of(line));
        return request;
    }

    @Test
    void createReturnRejectsADuplicateReturnNumber() {
        when(salesReturnRepository.existsByReturnNumber("RET-1")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> service.createReturn(request(2)));
    }

    @Test
    void createReturnRejectsAgainstACancelledOrder() {
        when(salesReturnRepository.existsByReturnNumber("RET-1")).thenReturn(false);
        order.setStatus(OrderStatus.CANCELLED);

        assertThrows(IllegalStateException.class, () -> service.createReturn(request(2)));
    }

    @Test
    void createReturnRejectsReturningMoreThanWasSold() {
        when(salesReturnRepository.existsByReturnNumber("RET-1")).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> service.createReturn(request(5)));
    }

    @Test
    void createReturnRejectsReturningMoreThanRemainsAfterAPriorReturn() {
        when(salesReturnRepository.existsByReturnNumber("RET-1")).thenReturn(false);
        when(salesReturnLineRepository.sumQuantityReturnedByOrderLineId(200L)).thenReturn(3);

        assertThrows(IllegalArgumentException.class, () -> service.createReturn(request(2)));
    }

    @Test
    void createReturnRestoresStockAndPostsABalancedReversalEntry() {
        when(salesReturnRepository.existsByReturnNumber("RET-1")).thenReturn(false);
        JournalEntry entry = JournalEntry.builder().id(900L).entryNumber(90L).build();
        ArgumentCaptor<List<ManualLineSpec>> captor = ArgumentCaptor.forClass(List.class);
        when(glPostingService.postManual(eq("SALES-RETURN-300"), eq(LocalDate.of(2026, 8, 15)), anyString(),
                any(), eq("SALES_RETURN"), eq(300L), captor.capture(), eq("clerk1")))
                .thenReturn(entry);

        SalesReturnResponse response = service.createReturn(request(2));

        assertEquals(0, new BigDecimal("20.00").compareTo(response.getTotalRefundAmount()));
        assertEquals(0, new BigDecimal("2.00").compareTo(response.getTotalTaxReversed()));
        assertEquals(0, new BigDecimal("12.00").compareTo(response.getTotalCostReversed()));
        verify(shopInventoryService).addStock(1L, 1L, 2);

        List<ManualLineSpec> specs = captor.getValue();
        BigDecimal totalDebits = specs.stream().map(ManualLineSpec::debitAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCredits = specs.stream().map(ManualLineSpec::creditAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, totalDebits.compareTo(totalCredits));

        ManualLineSpec cashLine = specs.stream().filter(s -> s.account() == cash).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("20.00").compareTo(cashLine.creditAmount()));
        ManualLineSpec returnsLine = specs.stream().filter(s -> s.account() == salesReturns).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("18.00").compareTo(returnsLine.debitAmount()));
        ManualLineSpec inventoryLine = specs.stream().filter(s -> s.account() == inventory).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("12.00").compareTo(inventoryLine.debitAmount()));
    }

    @Test
    void createReturnSkipsCostReversalWhenUnitCostIsUnknown() {
        orderLine.setUnitCost(null);
        when(salesReturnRepository.existsByReturnNumber("RET-1")).thenReturn(false);
        JournalEntry entry = JournalEntry.builder().id(900L).entryNumber(90L).build();
        ArgumentCaptor<List<ManualLineSpec>> captor = ArgumentCaptor.forClass(List.class);
        when(glPostingService.postManual(anyString(), any(LocalDate.class), anyString(),
                any(), anyString(), anyLong(), captor.capture(), anyString()))
                .thenReturn(entry);

        SalesReturnResponse response = service.createReturn(request(2));

        assertNull(response.getTotalCostReversed());
        List<ManualLineSpec> specs = captor.getValue();
        assertTrue(specs.stream().noneMatch(s -> s.account() == inventory || s.account() == cogs));
    }
}
