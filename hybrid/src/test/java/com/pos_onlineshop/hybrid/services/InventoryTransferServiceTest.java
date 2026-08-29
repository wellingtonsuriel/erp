package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.cashier.Cashier;
import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.damagedStockReceived.DamagedStockReceivedRepository;
import com.pos_onlineshop.hybrid.enums.InventoryMovementType;
import com.pos_onlineshop.hybrid.enums.TransferStatus;
import com.pos_onlineshop.hybrid.inventoryTransfer.InventoryTransfer;
import com.pos_onlineshop.hybrid.inventoryTransfer.InventoryTransferRepository;
import com.pos_onlineshop.hybrid.inventoryTransferItems.InventoryTransferItem;
import com.pos_onlineshop.hybrid.products.Product;
import com.pos_onlineshop.hybrid.products.ProductRepository;
import com.pos_onlineshop.hybrid.shop.Shop;
import com.pos_onlineshop.hybrid.shop.ShopRepository;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryTransferServiceTest {

    @Mock private InventoryTransferRepository transferRepository;
    @Mock private ShopRepository shopRepository;
    @Mock private ProductRepository productRepository;
    @Mock private ShopInventoryService shopInventoryService;
    @Mock private DamagedStockReceivedRepository damagedStockReceivedRepository;
    @Mock private GLPostingService glPostingService;
    @Mock private CurrencyService currencyService;
    @Mock private InventoryValuationService inventoryValuationService;

    private InventoryTransferService service;

    private Shop fromShop;
    private Shop toShop;
    private Product product;
    private Currency currency;
    private Cashier actor;

    @BeforeEach
    void setUp() {
        service = new InventoryTransferService(transferRepository, shopRepository, productRepository,
                shopInventoryService, damagedStockReceivedRepository, glPostingService, currencyService,
                inventoryValuationService);

        currency = Currency.builder().id(1L).code("USD").build();
        fromShop = Shop.builder().id(1L).code("SHOP-A").name("Shop A").defaultCurrency(currency).build();
        toShop = Shop.builder().id(2L).code("SHOP-B").name("Shop B").defaultCurrency(currency).build();
        product = Product.builder().id(1L).name("Widget").build();
        actor = Cashier.builder().id(1L).username("clerk1").build();
    }

    private InventoryTransferItem item(int requestedQuantity) {
        return InventoryTransferItem.builder().id(10L).product(product)
                .requestedQuantity(requestedQuantity).unitCost(new BigDecimal("5.00")).build();
    }

    private InventoryTransfer approvedTransfer(InventoryTransferItem transferItem) {
        InventoryTransfer transfer = InventoryTransfer.builder().id(100L).fromShop(fromShop).toShop(toShop)
                .transferNumber("TRF-1").status(TransferStatus.APPROVED).build();
        transfer.addTransferItem(transferItem);
        return transfer;
    }

    private InventoryTransfer inTransitTransfer(InventoryTransferItem transferItem) {
        InventoryTransfer transfer = InventoryTransfer.builder().id(100L).fromShop(fromShop).toShop(toShop)
                .transferNumber("TRF-1").status(TransferStatus.IN_TRANSIT).build();
        transfer.addTransferItem(transferItem);
        transferItem.setShippedQuantity(transferItem.getRequestedQuantity());
        return transfer;
    }

    // ------------------------------------------------------------------
    // shipTransfer - FIFO consumption from the source shop
    // ------------------------------------------------------------------

    @Test
    void shipTransferConsumesSourceShopFifoLayersAndOverwritesTheManualUnitCost() {
        InventoryTransferItem transferItem = item(20);
        InventoryTransfer transfer = approvedTransfer(transferItem);
        when(transferRepository.findById(100L)).thenReturn(Optional.of(transfer));
        when(shopInventoryService.isInStock(1L, 1L, 20)).thenReturn(true);
        when(inventoryValuationService.consumeCostLayers(fromShop, product, 20, InventoryMovementType.TRANSFER_OUT,
                "TRANSFER-100", LocalDate.now()))
                .thenReturn(InventoryValuationService.CostResult.builder()
                        .totalCost(new BigDecimal("140.00")).quantityCosted(20).quantityRequested(20)
                        .fullyCosted(true).build());
        when(transferRepository.save(any(InventoryTransfer.class))).thenAnswer(inv -> inv.getArgument(0));

        service.shipTransfer(100L, actor);

        verify(shopInventoryService).reduceStock(1L, 1L, 20);
        // Real FIFO cost (140.00 / 20 = 7.00) supersedes the manually-entered 5.00.
        assertEquals(0, new BigDecimal("7.0000").compareTo(transferItem.getUnitCost()));
    }

    @Test
    void shipTransferUsesOnlyTheCoveredPortionWhenLayersDontFullyCoverTheShipment() {
        InventoryTransferItem transferItem = item(20);
        InventoryTransfer transfer = approvedTransfer(transferItem);
        when(transferRepository.findById(100L)).thenReturn(Optional.of(transfer));
        when(shopInventoryService.isInStock(1L, 1L, 20)).thenReturn(true);
        when(inventoryValuationService.consumeCostLayers(fromShop, product, 20, InventoryMovementType.TRANSFER_OUT,
                "TRANSFER-100", LocalDate.now()))
                .thenReturn(InventoryValuationService.CostResult.builder()
                        .totalCost(new BigDecimal("50.00")).quantityCosted(10).quantityRequested(20)
                        .fullyCosted(false).build());
        when(transferRepository.save(any(InventoryTransfer.class))).thenAnswer(inv -> inv.getArgument(0));

        service.shipTransfer(100L, actor);

        // 50.00 / 10 covered units = 5.00 - never a guess for the uncovered portion.
        assertEquals(0, new BigDecimal("5.0000").compareTo(transferItem.getUnitCost()));
    }

    @Test
    void shipTransferSetsNullUnitCostWhenNoLayersCoverTheShipmentAtAll() {
        InventoryTransferItem transferItem = item(20);
        InventoryTransfer transfer = approvedTransfer(transferItem);
        when(transferRepository.findById(100L)).thenReturn(Optional.of(transfer));
        when(shopInventoryService.isInStock(1L, 1L, 20)).thenReturn(true);
        when(inventoryValuationService.consumeCostLayers(fromShop, product, 20, InventoryMovementType.TRANSFER_OUT,
                "TRANSFER-100", LocalDate.now()))
                .thenReturn(InventoryValuationService.CostResult.builder()
                        .totalCost(BigDecimal.ZERO).quantityCosted(0).quantityRequested(20)
                        .fullyCosted(false).build());
        when(transferRepository.save(any(InventoryTransfer.class))).thenAnswer(inv -> inv.getArgument(0));

        service.shipTransfer(100L, actor);

        assertNull(transferItem.getUnitCost());
    }

    // ------------------------------------------------------------------
    // receiveTransfer - destination cost layer creation
    // ------------------------------------------------------------------

    @Test
    void receiveTransferCreatesADestinationCostLayerAtTheRealShipCost() {
        InventoryTransferItem transferItem = item(20);
        transferItem.setUnitCost(new BigDecimal("7.00")); // set by shipTransfer in real flow
        InventoryTransfer transfer = inTransitTransfer(transferItem);
        when(transferRepository.findById(100L)).thenReturn(Optional.of(transfer));
        when(transferRepository.save(any(InventoryTransfer.class))).thenAnswer(inv -> inv.getArgument(0));

        service.receiveTransfer(100L, actor,
                List.of(new InventoryTransferService.ReceiveItemDto(1L, 20, 0)));

        verify(shopInventoryService).addStock(2L, 1L, 20);
        verify(inventoryValuationService).restoreCostLayer(toShop, product, 20, new BigDecimal("7.00"),
                currency, InventoryMovementType.TRANSFER_IN, "TRANSFER_IN-100", LocalDate.now());
    }

    @Test
    void receiveTransferSkipsCostLayerCreationWhenUnitCostIsUnknown() {
        InventoryTransferItem transferItem = item(20);
        transferItem.setUnitCost(null); // shipTransfer couldn't cost it either
        InventoryTransfer transfer = inTransitTransfer(transferItem);
        when(transferRepository.findById(100L)).thenReturn(Optional.of(transfer));
        when(transferRepository.save(any(InventoryTransfer.class))).thenAnswer(inv -> inv.getArgument(0));

        service.receiveTransfer(100L, actor,
                List.of(new InventoryTransferService.ReceiveItemDto(1L, 20, 0)));

        verify(shopInventoryService).addStock(2L, 1L, 20);
        verify(inventoryValuationService, never()).restoreCostLayer(any(), any(), anyInt(), any(), any(), any(), anyString(), any());
    }

    // ------------------------------------------------------------------
    // cancelTransfer - restoring the source shop's cost layer
    // ------------------------------------------------------------------

    @Test
    void cancellingAnInTransitTransferRestoresTheSourceShopCostLayer() {
        InventoryTransferItem transferItem = item(20);
        transferItem.setUnitCost(new BigDecimal("7.00")); // set by shipTransfer in real flow
        InventoryTransfer transfer = inTransitTransfer(transferItem);
        when(transferRepository.findById(100L)).thenReturn(Optional.of(transfer));
        when(transferRepository.save(any(InventoryTransfer.class))).thenAnswer(inv -> inv.getArgument(0));

        service.cancelTransfer(100L, "Wrong destination");

        verify(shopInventoryService).addStock(1L, 1L, 20);
        verify(inventoryValuationService).restoreCostLayer(fromShop, product, 20, new BigDecimal("7.00"),
                currency, InventoryMovementType.ADJUSTMENT_IN, "TRANSFER-CANCEL-100", LocalDate.now());
    }

    @Test
    void cancellingAnInTransitTransferSkipsCostRestorationWhenUnitCostIsUnknown() {
        InventoryTransferItem transferItem = item(20);
        transferItem.setUnitCost(null);
        InventoryTransfer transfer = inTransitTransfer(transferItem);
        when(transferRepository.findById(100L)).thenReturn(Optional.of(transfer));
        when(transferRepository.save(any(InventoryTransfer.class))).thenAnswer(inv -> inv.getArgument(0));

        service.cancelTransfer(100L, "Wrong destination");

        verify(shopInventoryService).addStock(1L, 1L, 20);
        verify(inventoryValuationService, never()).restoreCostLayer(any(), any(), anyInt(), any(), any(), any(), anyString(), any());
    }
}
