package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.currency.CurrencyRepository;
import com.pos_onlineshop.hybrid.dtos.CreateSupplierInvoiceRequest;
import com.pos_onlineshop.hybrid.enums.FinancialEventType;
import com.pos_onlineshop.hybrid.gl.FinancialEvent;
import com.pos_onlineshop.hybrid.journalEntry.JournalEntry;
import com.pos_onlineshop.hybrid.journalEntry.JournalEntryRepository;
import com.pos_onlineshop.hybrid.products.ProductRepository;
import com.pos_onlineshop.hybrid.purchaseOrder.PurchaseOrder;
import com.pos_onlineshop.hybrid.purchaseOrder.PurchaseOrderRepository;
import com.pos_onlineshop.hybrid.shop.Shop;
import com.pos_onlineshop.hybrid.shop.ShopRepository;
import com.pos_onlineshop.hybrid.suppliers.Suppliers;
import com.pos_onlineshop.hybrid.suppliers.SuppliersRepository;
import com.pos_onlineshop.hybrid.supplierInvoice.SupplierInvoice;
import com.pos_onlineshop.hybrid.supplierInvoice.SupplierInvoiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SupplierInvoiceServiceTest {

    @Mock private SupplierInvoiceRepository supplierInvoiceRepository;
    @Mock private SuppliersRepository suppliersRepository;
    @Mock private ShopRepository shopRepository;
    @Mock private CurrencyRepository currencyRepository;
    @Mock private ProductRepository productRepository;
    @Mock private PurchaseOrderRepository purchaseOrderRepository;
    @Mock private JournalEntryRepository journalEntryRepository;
    @Mock private GLPostingService glPostingService;
    @Mock private CurrencyService currencyService;

    private SupplierInvoiceService service;
    private Suppliers supplier;
    private Shop shop;
    private Currency currency;

    @BeforeEach
    void setUp() {
        service = new SupplierInvoiceService(supplierInvoiceRepository, suppliersRepository, shopRepository,
                currencyRepository, productRepository, purchaseOrderRepository, journalEntryRepository,
                glPostingService, currencyService);

        supplier = Suppliers.builder().id(1L).name("Acme Supplies").build();
        shop = Shop.builder().id(1L).name("Main Shop").build();
        currency = Currency.builder().id(1L).code("USD").build();

        lenient().when(supplierInvoiceRepository.save(any(SupplierInvoice.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(currencyService.getBaseCurrency()).thenReturn(currency);
    }

    private CreateSupplierInvoiceRequest request(Long poId) {
        CreateSupplierInvoiceRequest request = new CreateSupplierInvoiceRequest();
        request.setInvoiceNumber("INV-1");
        request.setSupplierId(1L);
        request.setShopId(1L);
        request.setPurchaseOrderId(poId);
        request.setCurrencyId(1L);
        request.setInvoiceDate(LocalDate.now());
        request.setDueDate(LocalDate.now().plusDays(30));
        request.setSubtotalAmount(new BigDecimal("100.00"));
        request.setTaxAmount(new BigDecimal("15.00"));
        return request;
    }

    @Test
    void createInvoiceRejectsDuplicateInvoiceNumber() {
        when(supplierInvoiceRepository.existsByInvoiceNumber("INV-1")).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> service.createInvoice(request(null)));
    }

    @Test
    void postingAStandaloneInvoicePostsToTheGeneralLedger() {
        when(suppliersRepository.findById(1L)).thenReturn(Optional.of(supplier));
        when(shopRepository.findById(1L)).thenReturn(Optional.of(shop));
        when(currencyRepository.findById(1L)).thenReturn(Optional.of(currency));

        SupplierInvoice invoice = service.createInvoice(request(null));
        invoice.setId(1L);
        when(supplierInvoiceRepository.findById(1L)).thenReturn(Optional.of(invoice));

        service.postInvoice(1L);

        ArgumentCaptor<FinancialEvent> captor = ArgumentCaptor.forClass(FinancialEvent.class);
        verify(glPostingService, times(1)).post(captor.capture());
        FinancialEvent event = captor.getValue();
        assertEquals(FinancialEventType.PURCHASE_INVOICE, event.getEventType());
        assertEquals("SUPPLIER-INVOICE-1", event.getIdempotencyKey());
        assertEquals(0, new BigDecimal("115.00").compareTo(event.getGrossAmount()));
        assertEquals(0, new BigDecimal("100.00").compareTo(event.getNetAmount()));
        assertEquals(0, new BigDecimal("15.00").compareTo(event.getTaxAmount()));
    }

    @Test
    void postingAPoLinkedInvoiceDoesNotPostToTheGeneralLedger() {
        PurchaseOrder po = PurchaseOrder.builder().id(5L).poNumber("PO-5").build();
        when(suppliersRepository.findById(1L)).thenReturn(Optional.of(supplier));
        when(shopRepository.findById(1L)).thenReturn(Optional.of(shop));
        when(currencyRepository.findById(1L)).thenReturn(Optional.of(currency));
        when(purchaseOrderRepository.findById(5L)).thenReturn(Optional.of(po));

        SupplierInvoice invoice = service.createInvoice(request(5L));
        invoice.setId(2L);
        when(supplierInvoiceRepository.findById(2L)).thenReturn(Optional.of(invoice));

        SupplierInvoice result = service.postInvoice(2L);

        assertTrue(result.isPoLinked());
        verifyNoInteractions(glPostingService);
    }

    @Test
    void voidingAPostedStandaloneInvoiceReversesItsJournalEntry() {
        when(suppliersRepository.findById(1L)).thenReturn(Optional.of(supplier));
        when(shopRepository.findById(1L)).thenReturn(Optional.of(shop));
        when(currencyRepository.findById(1L)).thenReturn(Optional.of(currency));

        SupplierInvoice invoice = service.createInvoice(request(null));
        invoice.setId(3L);
        when(supplierInvoiceRepository.findById(3L)).thenReturn(Optional.of(invoice));
        service.postInvoice(3L);

        JournalEntry originalEntry = JournalEntry.builder().id(9L).entryNumber(1L).idempotencyKey("SUPPLIER-INVOICE-3").build();
        when(journalEntryRepository.findByIdempotencyKey("SUPPLIER-INVOICE-3")).thenReturn(Optional.of(originalEntry));

        service.voidInvoice(3L, "duplicate");

        verify(glPostingService, times(1)).reverse(eq(originalEntry), any(LocalDate.class), eq("Invoice voided: duplicate"), any());
    }

    @Test
    void voidingADraftInvoiceNeverTouchesTheGeneralLedger() {
        when(suppliersRepository.findById(1L)).thenReturn(Optional.of(supplier));
        when(shopRepository.findById(1L)).thenReturn(Optional.of(shop));
        when(currencyRepository.findById(1L)).thenReturn(Optional.of(currency));

        SupplierInvoice invoice = service.createInvoice(request(null));
        invoice.setId(4L);
        when(supplierInvoiceRepository.findById(4L)).thenReturn(Optional.of(invoice));

        service.voidInvoice(4L, "created by mistake");

        verifyNoInteractions(glPostingService, journalEntryRepository);
    }
}
