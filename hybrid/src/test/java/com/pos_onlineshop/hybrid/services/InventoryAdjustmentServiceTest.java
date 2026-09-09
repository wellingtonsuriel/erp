package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.account.Account;
import com.pos_onlineshop.hybrid.account.AccountRepository;
import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.dtos.CreateInventoryAdjustmentRequest;
import com.pos_onlineshop.hybrid.dtos.InventoryAdjustmentResponse;
import com.pos_onlineshop.hybrid.enums.AccountType;
import com.pos_onlineshop.hybrid.enums.DebitCredit;
import com.pos_onlineshop.hybrid.enums.InventoryMovementType;
import com.pos_onlineshop.hybrid.gl.ManualLineSpec;
import com.pos_onlineshop.hybrid.inventoryAdjustment.InventoryAdjustment;
import com.pos_onlineshop.hybrid.inventoryAdjustment.InventoryAdjustmentRepository;
import com.pos_onlineshop.hybrid.journalEntry.JournalEntry;
import com.pos_onlineshop.hybrid.products.Product;
import com.pos_onlineshop.hybrid.products.ProductRepository;
import com.pos_onlineshop.hybrid.shop.Shop;
import com.pos_onlineshop.hybrid.shop.ShopRepository;
import com.pos_onlineshop.hybrid.shopInventory.ShopInventory;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Proves the core invariant InventoryAdjustment exists for: a manual stock-count correction
 * must move the same quantity through InventoryTotal (via ShopInventoryService) and through
 * the FIFO valuation ledger (via InventoryValuationService) and post the matching monetary
 * amount to the GL - never a quantity change with no accounting effect, and never a GL entry
 * with no matching quantity change.
 */
@ExtendWith(MockitoExtension.class)
class InventoryAdjustmentServiceTest {

    @Mock private InventoryAdjustmentRepository inventoryAdjustmentRepository;
    @Mock private ShopRepository shopRepository;
    @Mock private ProductRepository productRepository;
    @Mock private UserAccountRepository userAccountRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private ShopInventoryService shopInventoryService;
    @Mock private InventoryValuationService inventoryValuationService;
    @Mock private CurrencyService currencyService;
    @Mock private GLPostingService glPostingService;

    private InventoryAdjustmentService service;

    private Shop shop;
    private Product product;
    private UserAccount user;
    private Currency baseCurrency;
    private Account inventoryAsset;
    private Account adjustmentGainLoss;

    @BeforeEach
    void setUp() {
        service = new InventoryAdjustmentService(inventoryAdjustmentRepository, shopRepository, productRepository,
                userAccountRepository, accountRepository, shopInventoryService, inventoryValuationService,
                currencyService, glPostingService);

        shop = Shop.builder().id(1L).name("Main Shop").build();
        product = Product.builder().id(2L).name("Widget").build();
        user = UserAccount.builder().id(3L).username("stockcounter").password("x").email("s@test.com").build();
        baseCurrency = Currency.builder().id(1L).code("USD").build();
        inventoryAsset = Account.builder().id(10L).code("1200").name("Inventory Asset")
                .accountType(AccountType.ASSET).normalBalance(DebitCredit.DEBIT).controlAccount(true).active(true).build();
        adjustmentGainLoss = Account.builder().id(11L).code("5110").name("Inventory Adjustment Gain / Loss")
                .accountType(AccountType.EXPENSE).normalBalance(DebitCredit.DEBIT).active(true).build();

        lenient().when(shopRepository.findById(1L)).thenReturn(Optional.of(shop));
        lenient().when(productRepository.findById(2L)).thenReturn(Optional.of(product));
        lenient().when(userAccountRepository.findById(3L)).thenReturn(Optional.of(user));
        lenient().when(currencyService.getBaseCurrency()).thenReturn(baseCurrency);
        lenient().when(accountRepository.findByCode("1200")).thenReturn(Optional.of(inventoryAsset));
        lenient().when(accountRepository.findByCode("5110")).thenReturn(Optional.of(adjustmentGainLoss));
        lenient().when(inventoryAdjustmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private CreateInventoryAdjustmentRequest surplusRequest() {
        CreateInventoryAdjustmentRequest request = new CreateInventoryAdjustmentRequest();
        request.setReference("ADJ-1");
        request.setShopId(1L);
        request.setProductId(2L);
        request.setQuantityDelta(10);
        request.setReason("Stock count found surplus");
        request.setUnitCost(new BigDecimal("5.00"));
        return request;
    }

    @Test
    void surplusAdjustmentCreatesAFifoLayerAndIncreasesInventoryTotalBeforePosting() {
        when(inventoryAdjustmentRepository.findByReference("ADJ-1")).thenReturn(Optional.empty());
        when(glPostingService.postManual(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(JournalEntry.builder().id(99L).entryNumber(42L).build());

        InventoryAdjustmentResponse response = service.createAdjustment(surplusRequest(), 3L);

        verify(shopInventoryService).addStock(eq(1L), eq(2L), eq(10), any());
        verify(inventoryValuationService).restoreCostLayer(eq(shop), eq(product), eq(10), eq(new BigDecimal("5.00")),
                eq(baseCurrency), eq(InventoryMovementType.ADJUSTMENT_IN), eq("ADJ-1"), any(LocalDate.class));

        assertEquals(new BigDecimal("50.00"), response.getTotalValue());
        assertEquals(99L, response.getPostedJournalEntryId());
    }

    @Test
    void surplusAdjustmentPostsDebitInventoryAssetCreditGainForTheSameAmount() {
        when(inventoryAdjustmentRepository.findByReference("ADJ-1")).thenReturn(Optional.empty());
        when(glPostingService.postManual(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(JournalEntry.builder().id(99L).entryNumber(42L).build());

        service.createAdjustment(surplusRequest(), 3L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ManualLineSpec>> specsCaptor = ArgumentCaptor.forClass(List.class);
        verify(glPostingService).postManual(eq("INVENTORY-ADJUSTMENT-ADJ-1"), any(), any(), any(), any(), any(),
                specsCaptor.capture(), any());

        List<ManualLineSpec> specs = specsCaptor.getValue();
        assertEquals(2, specs.size());
        ManualLineSpec debitLine = specs.stream().filter(s -> s.debitAmount().compareTo(BigDecimal.ZERO) > 0).findFirst().orElseThrow();
        ManualLineSpec creditLine = specs.stream().filter(s -> s.creditAmount().compareTo(BigDecimal.ZERO) > 0).findFirst().orElseThrow();
        assertEquals(inventoryAsset, debitLine.account());
        assertEquals(adjustmentGainLoss, creditLine.account());
        assertEquals(0, debitLine.debitAmount().compareTo(creditLine.creditAmount()));
    }

    @Test
    void surplusAdjustmentWithoutAUnitCostIsRejected() {
        CreateInventoryAdjustmentRequest request = surplusRequest();
        request.setUnitCost(null);

        assertThrows(IllegalArgumentException.class, () -> service.createAdjustment(request, 3L));
        verifyNoInteractions(shopInventoryService, inventoryValuationService, glPostingService);
    }

    @Test
    void shortageAdjustmentConsumesRealFifoLayersAndReducesInventoryTotal() {
        CreateInventoryAdjustmentRequest request = new CreateInventoryAdjustmentRequest();
        request.setReference("ADJ-2");
        request.setShopId(1L);
        request.setProductId(2L);
        request.setQuantityDelta(-4);
        request.setReason("Stock count found shortage");

        when(inventoryAdjustmentRepository.findByReference("ADJ-2")).thenReturn(Optional.empty());
        when(inventoryValuationService.consumeCostLayers(eq(shop), eq(product), eq(4),
                eq(InventoryMovementType.ADJUSTMENT_OUT), eq("ADJ-2"), any(LocalDate.class)))
                .thenReturn(InventoryValuationService.CostResult.builder()
                        .totalCost(new BigDecimal("12.00")).quantityCosted(4).quantityRequested(4).fullyCosted(true).build());
        when(glPostingService.postManual(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(JournalEntry.builder().id(100L).entryNumber(43L).build());

        InventoryAdjustmentResponse response = service.createAdjustment(request, 3L);

        verify(shopInventoryService).reduceStock(eq(1L), eq(2L), eq(4), any());
        assertEquals(new BigDecimal("12.00"), response.getTotalValue());
        assertTrue(response.isFullyCosted());
    }

    @Test
    void shortageAdjustmentStillReducesStockButSkipsGlPostingWhenNothingCouldBeCosted() {
        CreateInventoryAdjustmentRequest request = new CreateInventoryAdjustmentRequest();
        request.setReference("ADJ-3");
        request.setShopId(1L);
        request.setProductId(2L);
        request.setQuantityDelta(-4);
        request.setReason("Stock count found shortage, no cost layers on record");

        when(inventoryAdjustmentRepository.findByReference("ADJ-3")).thenReturn(Optional.empty());
        when(inventoryValuationService.consumeCostLayers(eq(shop), eq(product), eq(4),
                eq(InventoryMovementType.ADJUSTMENT_OUT), eq("ADJ-3"), any(LocalDate.class)))
                .thenReturn(InventoryValuationService.CostResult.builder()
                        .totalCost(BigDecimal.ZERO).quantityCosted(0).quantityRequested(4).fullyCosted(false).build());

        InventoryAdjustmentResponse response = service.createAdjustment(request, 3L);

        verify(shopInventoryService).reduceStock(eq(1L), eq(2L), eq(4), any());
        verifyNoInteractions(glPostingService);
        assertEquals(BigDecimal.ZERO, response.getTotalValue());
        assertNull(response.getPostedJournalEntryId());
    }

    @Test
    void retryingWithTheSameReferenceReplaysTheOriginalAdjustmentRatherThanCreatingASecondOne() {
        InventoryAdjustment existing = InventoryAdjustment.builder()
                .id(5L).reference("ADJ-1").shop(shop).product(product).quantityDelta(10)
                .reason("Stock count found surplus").unitCost(new BigDecimal("5.00")).totalValue(new BigDecimal("50.00"))
                .createdBy(user).build();
        when(inventoryAdjustmentRepository.findByReference("ADJ-1")).thenReturn(Optional.of(existing));

        InventoryAdjustmentResponse response = service.createAdjustment(surplusRequest(), 3L);

        assertEquals(5L, response.getId());
        verifyNoInteractions(shopInventoryService, inventoryValuationService, glPostingService, accountRepository);
    }

    @Test
    void zeroQuantityDeltaIsRejected() {
        CreateInventoryAdjustmentRequest request = surplusRequest();
        request.setQuantityDelta(0);
        when(inventoryAdjustmentRepository.findByReference("ADJ-1")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.createAdjustment(request, 3L));
    }
}
