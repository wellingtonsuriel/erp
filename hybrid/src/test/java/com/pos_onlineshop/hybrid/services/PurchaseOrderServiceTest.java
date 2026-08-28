package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.cashier.Cashier;
import com.pos_onlineshop.hybrid.cashier.CashierRepository;
import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.currency.CurrencyRepository;
import com.pos_onlineshop.hybrid.dtos.CreatePurchaseOrderRequest;
import com.pos_onlineshop.hybrid.dtos.CreateShopInventoryRequest;
import com.pos_onlineshop.hybrid.dtos.ReceivePurchaseOrderRequest;
import com.pos_onlineshop.hybrid.enums.PurchaseOrderStatus;
import com.pos_onlineshop.hybrid.products.Product;
import com.pos_onlineshop.hybrid.products.ProductRepository;
import com.pos_onlineshop.hybrid.purchaseOrder.PurchaseOrder;
import com.pos_onlineshop.hybrid.purchaseOrder.PurchaseOrderRepository;
import com.pos_onlineshop.hybrid.purchaseOrderLine.PurchaseOrderLine;
import com.pos_onlineshop.hybrid.shop.Shop;
import com.pos_onlineshop.hybrid.shop.ShopRepository;
import com.pos_onlineshop.hybrid.suppliers.Suppliers;
import com.pos_onlineshop.hybrid.suppliers.SuppliersRepository;
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

@ExtendWith(MockitoExtension.class)
class PurchaseOrderServiceTest {

    @Mock private PurchaseOrderRepository purchaseOrderRepository;
    @Mock private SuppliersRepository suppliersRepository;
    @Mock private ShopRepository shopRepository;
    @Mock private CurrencyRepository currencyRepository;
    @Mock private ProductRepository productRepository;
    @Mock private CashierRepository cashierRepository;
    @Mock private ShopInventoryService shopInventoryService;

    private PurchaseOrderService service;

    private Suppliers supplier;
    private Shop shop;
    private Currency currency;
    private Product product;

    @BeforeEach
    void setUp() {
        service = new PurchaseOrderService(purchaseOrderRepository, suppliersRepository, shopRepository,
                currencyRepository, productRepository, cashierRepository, shopInventoryService);

        supplier = Suppliers.builder().id(1L).name("Acme Supplies").build();
        shop = Shop.builder().id(1L).name("Main Shop").build();
        currency = Currency.builder().id(1L).code("USD").build();
        product = Product.builder().id(1L).name("Widget").build();

        // lenient: not every test in this class reaches a save() call (some assert an
        // exception is thrown first), and this stub is shared setup rather than per-test intent.
        lenient().when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private CreatePurchaseOrderRequest createRequest(int quantity, String unitCost) {
        CreatePurchaseOrderRequest request = new CreatePurchaseOrderRequest();
        request.setSupplierId(1L);
        request.setShopId(1L);
        request.setCurrencyId(1L);
        CreatePurchaseOrderRequest.Line line = new CreatePurchaseOrderRequest.Line();
        line.setProductId(1L);
        line.setQuantity(quantity);
        line.setUnitCost(new BigDecimal(unitCost));
        request.setLines(List.of(line));
        return request;
    }

    @Test
    void createPurchaseOrderBuildsADraftWithResolvedReferences() {
        when(suppliersRepository.findById(1L)).thenReturn(Optional.of(supplier));
        when(shopRepository.findById(1L)).thenReturn(Optional.of(shop));
        when(currencyRepository.findById(1L)).thenReturn(Optional.of(currency));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        PurchaseOrder po = service.createPurchaseOrder(createRequest(10, "5.00"));

        assertEquals(PurchaseOrderStatus.DRAFT, po.getStatus());
        assertEquals(supplier, po.getSupplier());
        assertEquals(1, po.getLines().size());
        assertEquals(0, new BigDecimal("50.00").compareTo(po.getTotalValue()));
    }

    @Test
    void createPurchaseOrderRejectsUnknownSupplier() {
        when(suppliersRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.createPurchaseOrder(createRequest(10, "5.00")));
    }

    private PurchaseOrder approvedPoWithOneLine(int orderedQuantity) {
        PurchaseOrder po = PurchaseOrder.builder()
                .id(1L).poNumber("PO-1").supplier(supplier).shop(shop).currency(currency)
                .status(PurchaseOrderStatus.APPROVED).build();
        po.addLine(PurchaseOrderLine.builder()
                .id(1L).product(product).quantityOrdered(orderedQuantity).unitCost(new BigDecimal("5.00")).build());
        return po;
    }

    @Test
    void receiveCallsShopInventoryServiceOncePerLineWithPoSupplierAndCost() {
        PurchaseOrder po = approvedPoWithOneLine(10);
        when(purchaseOrderRepository.findById(1L)).thenReturn(Optional.of(po));

        ReceivePurchaseOrderRequest request = new ReceivePurchaseOrderRequest();
        request.setReceivedById(2L);
        ReceivePurchaseOrderRequest.Line line = new ReceivePurchaseOrderRequest.Line();
        line.setProductId(1L);
        line.setReceivedQuantity(10);
        request.setLines(List.of(line));

        PurchaseOrder result = service.receive(1L, request);

        ArgumentCaptor<CreateShopInventoryRequest> captor = ArgumentCaptor.forClass(CreateShopInventoryRequest.class);
        verify(shopInventoryService, times(1)).createShopInventory(captor.capture());
        CreateShopInventoryRequest posted = captor.getValue();
        assertEquals(1L, posted.getShopId());
        assertEquals(1L, posted.getProductId());
        assertEquals(1L, posted.getSupplierId());
        assertEquals(1L, posted.getCurrencyId());
        assertEquals(10, posted.getQuantity());
        assertEquals(0, new BigDecimal("5.00").compareTo(posted.getUnitPrice()));

        assertEquals(PurchaseOrderStatus.RECEIVED, result.getStatus());
        assertEquals(10, po.getLines().get(0).getQuantityReceived());
    }

    @Test
    void receiveRejectsOverReceiptWithoutCallingShopInventory() {
        PurchaseOrder po = approvedPoWithOneLine(5);
        when(purchaseOrderRepository.findById(1L)).thenReturn(Optional.of(po));

        ReceivePurchaseOrderRequest request = new ReceivePurchaseOrderRequest();
        request.setReceivedById(2L);
        ReceivePurchaseOrderRequest.Line line = new ReceivePurchaseOrderRequest.Line();
        line.setProductId(1L);
        line.setReceivedQuantity(999);
        request.setLines(List.of(line));

        assertThrows(IllegalArgumentException.class, () -> service.receive(1L, request));
        verifyNoInteractions(shopInventoryService);
    }

    @Test
    void receiveRejectsAPurchaseOrderThatIsNotApproved() {
        PurchaseOrder draftPo = PurchaseOrder.builder()
                .id(1L).poNumber("PO-DRAFT").supplier(supplier).shop(shop).currency(currency)
                .status(PurchaseOrderStatus.DRAFT).build();
        when(purchaseOrderRepository.findById(1L)).thenReturn(Optional.of(draftPo));

        ReceivePurchaseOrderRequest request = new ReceivePurchaseOrderRequest();
        ReceivePurchaseOrderRequest.Line line = new ReceivePurchaseOrderRequest.Line();
        line.setProductId(1L);
        line.setReceivedQuantity(1);
        request.setLines(List.of(line));

        assertThrows(IllegalStateException.class, () -> service.receive(1L, request));
        verifyNoInteractions(shopInventoryService);
    }

    @Test
    void partialReceiptAcrossTwoCallsEndsFullyReceived() {
        PurchaseOrder po = approvedPoWithOneLine(10);
        when(purchaseOrderRepository.findById(1L)).thenReturn(Optional.of(po));

        ReceivePurchaseOrderRequest first = new ReceivePurchaseOrderRequest();
        ReceivePurchaseOrderRequest.Line firstLine = new ReceivePurchaseOrderRequest.Line();
        firstLine.setProductId(1L);
        firstLine.setReceivedQuantity(4);
        first.setLines(List.of(firstLine));

        PurchaseOrder afterFirst = service.receive(1L, first);
        assertEquals(PurchaseOrderStatus.PARTIALLY_RECEIVED, afterFirst.getStatus());

        ReceivePurchaseOrderRequest second = new ReceivePurchaseOrderRequest();
        ReceivePurchaseOrderRequest.Line secondLine = new ReceivePurchaseOrderRequest.Line();
        secondLine.setProductId(1L);
        secondLine.setReceivedQuantity(6);
        second.setLines(List.of(secondLine));

        PurchaseOrder afterSecond = service.receive(1L, second);
        assertEquals(PurchaseOrderStatus.RECEIVED, afterSecond.getStatus());
        verify(shopInventoryService, times(2)).createShopInventory(any());
    }
}
