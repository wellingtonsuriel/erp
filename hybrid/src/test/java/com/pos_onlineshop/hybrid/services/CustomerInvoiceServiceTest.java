package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.currency.CurrencyRepository;
import com.pos_onlineshop.hybrid.customerInvoice.CustomerInvoice;
import com.pos_onlineshop.hybrid.customerInvoice.CustomerInvoiceRepository;
import com.pos_onlineshop.hybrid.customers.Customers;
import com.pos_onlineshop.hybrid.customers.CustomersRepository;
import com.pos_onlineshop.hybrid.dtos.CreateCustomerInvoiceRequest;
import com.pos_onlineshop.hybrid.enums.CustomerInvoiceStatus;
import com.pos_onlineshop.hybrid.enums.FinancialEventType;
import com.pos_onlineshop.hybrid.gl.FinancialEvent;
import com.pos_onlineshop.hybrid.journalEntry.JournalEntry;
import com.pos_onlineshop.hybrid.journalEntry.JournalEntryRepository;
import com.pos_onlineshop.hybrid.products.ProductRepository;
import com.pos_onlineshop.hybrid.shop.Shop;
import com.pos_onlineshop.hybrid.shop.ShopRepository;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerInvoiceServiceTest {

    @Mock private CustomerInvoiceRepository customerInvoiceRepository;
    @Mock private CustomersRepository customersRepository;
    @Mock private ShopRepository shopRepository;
    @Mock private CurrencyRepository currencyRepository;
    @Mock private ProductRepository productRepository;
    @Mock private JournalEntryRepository journalEntryRepository;
    @Mock private GLPostingService glPostingService;
    @Mock private CurrencyService currencyService;

    private CustomerInvoiceService service;
    private Shop shop;
    private Currency currency;

    @BeforeEach
    void setUp() {
        service = new CustomerInvoiceService(customerInvoiceRepository, customersRepository, shopRepository,
                currencyRepository, productRepository, journalEntryRepository, glPostingService, currencyService);

        shop = Shop.builder().id(1L).name("Main Shop").build();
        currency = Currency.builder().id(1L).code("USD").build();

        lenient().when(customerInvoiceRepository.save(any(CustomerInvoice.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(currencyService.getBaseCurrency()).thenReturn(currency);
        lenient().when(shopRepository.findById(1L)).thenReturn(Optional.of(shop));
        lenient().when(currencyRepository.findById(1L)).thenReturn(Optional.of(currency));
    }

    private CreateCustomerInvoiceRequest request(String subtotal, String tax) {
        CreateCustomerInvoiceRequest request = new CreateCustomerInvoiceRequest();
        request.setInvoiceNumber("CINV-1");
        request.setCustomerId(1L);
        request.setShopId(1L);
        request.setCurrencyId(1L);
        request.setInvoiceDate(LocalDate.now());
        request.setDueDate(LocalDate.now().plusDays(30));
        request.setSubtotalAmount(new BigDecimal(subtotal));
        request.setTaxAmount(new BigDecimal(tax));
        return request;
    }

    @Test
    void createInvoiceRejectsDuplicateInvoiceNumber() {
        when(customerInvoiceRepository.existsByInvoiceNumber("CINV-1")).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> service.createInvoice(request("100.00", "0.00")));
    }

    @Test
    void invoiceWithinCreditLimitIsAccepted() {
        Customers customer = Customers.builder().id(1L).name("Wholesale Co").creditLimit(new BigDecimal("500.00")).build();
        when(customersRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(customerInvoiceRepository.findByCustomerAndStatusIn(eq(customer), any())).thenReturn(List.of());

        CustomerInvoice invoice = service.createInvoice(request("100.00", "15.00"));
        assertEquals(0, new BigDecimal("115.00").compareTo(invoice.getTotalAmount()));
    }

    @Test
    void invoiceExceedingCreditLimitIsRejected() {
        Customers customer = Customers.builder().id(1L).name("Wholesale Co").creditLimit(new BigDecimal("100.00")).build();
        when(customersRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(customerInvoiceRepository.findByCustomerAndStatusIn(eq(customer), any())).thenReturn(List.of());

        assertThrows(IllegalArgumentException.class, () -> service.createInvoice(request("100.00", "15.00")));
    }

    @Test
    void existingOutstandingBalanceCountsTowardTheCreditLimit() {
        Customers customer = Customers.builder().id(1L).name("Wholesale Co").creditLimit(new BigDecimal("150.00")).build();
        when(customersRepository.findById(1L)).thenReturn(Optional.of(customer));

        CustomerInvoice existingOutstanding = CustomerInvoice.builder()
                .totalAmount(new BigDecimal("100.00")).amountPaid(BigDecimal.ZERO).build();
        when(customerInvoiceRepository.findByCustomerAndStatusIn(eq(customer), any())).thenReturn(List.of(existingOutstanding));

        // Existing 100 outstanding + new 100 = 200, over the 150 limit
        assertThrows(IllegalArgumentException.class, () -> service.createInvoice(request("100.00", "0.00")));
    }

    @Test
    void nullCreditLimitMeansUnlimited() {
        Customers customer = Customers.builder().id(1L).name("No Limit Co").creditLimit(null).build();
        when(customersRepository.findById(1L)).thenReturn(Optional.of(customer));

        CustomerInvoice invoice = service.createInvoice(request("100000.00", "0.00"));
        assertNotNull(invoice);
    }

    @Test
    void postingAnInvoicePostsToTheGeneralLedger() {
        Customers customer = Customers.builder().id(1L).name("Wholesale Co").build();
        when(customersRepository.findById(1L)).thenReturn(Optional.of(customer));

        CustomerInvoice invoice = service.createInvoice(request("100.00", "15.00"));
        invoice.setId(1L);
        when(customerInvoiceRepository.findById(1L)).thenReturn(Optional.of(invoice));

        service.postInvoice(1L);

        ArgumentCaptor<FinancialEvent> captor = ArgumentCaptor.forClass(FinancialEvent.class);
        verify(glPostingService, times(1)).post(captor.capture());
        FinancialEvent event = captor.getValue();
        assertEquals(FinancialEventType.CUSTOMER_INVOICE, event.getEventType());
        assertEquals("CUSTOMER-INVOICE-1", event.getIdempotencyKey());
        assertEquals(0, new BigDecimal("115.00").compareTo(event.getGrossAmount()));
    }

    @Test
    void postingAForeignCurrencyInvoicePersistsTheBookingRateOnTheEntity() {
        Customers customer = Customers.builder().id(1L).name("Wholesale Co").build();
        when(customersRepository.findById(1L)).thenReturn(Optional.of(customer));
        Currency eur = Currency.builder().id(2L).code("EUR").build();
        when(currencyRepository.findById(2L)).thenReturn(Optional.of(eur));
        when(currencyService.getExchangeRate(eur, currency)).thenReturn(new BigDecimal("1.08"));

        CreateCustomerInvoiceRequest request = request("100.00", "0.00");
        request.setCurrencyId(2L);
        CustomerInvoice invoice = service.createInvoice(request);
        invoice.setId(3L);
        when(customerInvoiceRepository.findById(3L)).thenReturn(Optional.of(invoice));

        service.postInvoice(3L);

        assertEquals(0, new BigDecimal("1.08").compareTo(invoice.getExchangeRate()));
    }

    @Test
    void voidingAPostedInvoiceReversesItsJournalEntry() {
        Customers customer = Customers.builder().id(1L).name("Wholesale Co").build();
        when(customersRepository.findById(1L)).thenReturn(Optional.of(customer));

        CustomerInvoice invoice = service.createInvoice(request("100.00", "0.00"));
        invoice.setId(2L);
        when(customerInvoiceRepository.findById(2L)).thenReturn(Optional.of(invoice));
        service.postInvoice(2L);

        JournalEntry originalEntry = JournalEntry.builder().id(9L).entryNumber(1L).idempotencyKey("CUSTOMER-INVOICE-2").build();
        when(journalEntryRepository.findByIdempotencyKey("CUSTOMER-INVOICE-2")).thenReturn(Optional.of(originalEntry));

        service.voidInvoice(2L, "duplicate");

        verify(glPostingService, times(1)).reverse(eq(originalEntry), any(LocalDate.class), eq("Invoice voided: duplicate"), any());
    }
}
