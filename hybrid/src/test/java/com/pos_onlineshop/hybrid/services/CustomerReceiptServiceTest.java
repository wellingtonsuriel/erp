package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.currency.CurrencyRepository;
import com.pos_onlineshop.hybrid.cashier.CashierRepository;
import com.pos_onlineshop.hybrid.customerInvoice.CustomerInvoice;
import com.pos_onlineshop.hybrid.customerInvoice.CustomerInvoiceRepository;
import com.pos_onlineshop.hybrid.customerReceipt.CustomerReceipt;
import com.pos_onlineshop.hybrid.customerReceipt.CustomerReceiptRepository;
import com.pos_onlineshop.hybrid.customers.Customers;
import com.pos_onlineshop.hybrid.dtos.RecordCustomerReceiptRequest;
import com.pos_onlineshop.hybrid.enums.CustomerInvoiceStatus;
import com.pos_onlineshop.hybrid.enums.FinancialEventType;
import com.pos_onlineshop.hybrid.enums.PaymentMethod;
import com.pos_onlineshop.hybrid.gl.FinancialEvent;
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
class CustomerReceiptServiceTest {

    @Mock private CustomerReceiptRepository customerReceiptRepository;
    @Mock private CustomerInvoiceRepository customerInvoiceRepository;
    @Mock private CurrencyRepository currencyRepository;
    @Mock private CashierRepository cashierRepository;
    @Mock private GLPostingService glPostingService;
    @Mock private CurrencyService currencyService;

    private CustomerReceiptService service;
    private Currency currency;
    private CustomerInvoice postedInvoice;

    @BeforeEach
    void setUp() {
        service = new CustomerReceiptService(customerReceiptRepository, customerInvoiceRepository,
                currencyRepository, cashierRepository, glPostingService, currencyService);

        currency = Currency.builder().id(1L).code("USD").build();
        Customers customer = Customers.builder().id(1L).name("Wholesale Co").build();
        postedInvoice = CustomerInvoice.builder()
                .id(10L).invoiceNumber("CINV-10").customer(customer)
                .invoiceDate(LocalDate.now()).dueDate(LocalDate.now().plusDays(30))
                .subtotalAmount(new BigDecimal("100.00")).taxAmount(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("100.00")).status(CustomerInvoiceStatus.POSTED)
                .build();

        lenient().when(currencyRepository.findById(1L)).thenReturn(Optional.of(currency));
        lenient().when(currencyService.getBaseCurrency()).thenReturn(currency);
        lenient().when(customerReceiptRepository.save(any(CustomerReceipt.class))).thenAnswer(inv -> {
            CustomerReceipt r = inv.getArgument(0);
            r.setId(99L);
            return r;
        });
    }

    private RecordCustomerReceiptRequest request(BigDecimal amount, PaymentMethod method) {
        RecordCustomerReceiptRequest request = new RecordCustomerReceiptRequest();
        request.setInvoiceId(10L);
        request.setAmount(amount);
        request.setCurrencyId(1L);
        request.setPaymentMethod(method);
        return request;
    }

    @Test
    void cashReceiptPostsCustomerReceiptCashEventType() {
        when(customerInvoiceRepository.findById(10L)).thenReturn(Optional.of(postedInvoice));

        service.recordReceipt(request(new BigDecimal("40.00"), PaymentMethod.CASH));

        ArgumentCaptor<FinancialEvent> captor = ArgumentCaptor.forClass(FinancialEvent.class);
        verify(glPostingService).post(captor.capture());
        assertEquals(FinancialEventType.CUSTOMER_RECEIPT_CASH, captor.getValue().getEventType());
        assertEquals("CUSTOMER-RECEIPT-99", captor.getValue().getIdempotencyKey());
    }

    @Test
    void nonCashReceiptPostsCustomerReceiptEventType() {
        when(customerInvoiceRepository.findById(10L)).thenReturn(Optional.of(postedInvoice));

        service.recordReceipt(request(new BigDecimal("40.00"), PaymentMethod.ECOCASH));

        ArgumentCaptor<FinancialEvent> captor = ArgumentCaptor.forClass(FinancialEvent.class);
        verify(glPostingService).post(captor.capture());
        assertEquals(FinancialEventType.CUSTOMER_RECEIPT, captor.getValue().getEventType());
    }

    @Test
    void fullReceiptMarksInvoicePaidAndUpdatesTheRepository() {
        when(customerInvoiceRepository.findById(10L)).thenReturn(Optional.of(postedInvoice));

        service.recordReceipt(request(new BigDecimal("100.00"), PaymentMethod.CASH));

        assertEquals(CustomerInvoiceStatus.PAID, postedInvoice.getStatus());
        verify(customerInvoiceRepository).save(postedInvoice);
    }

    @Test
    void overpaymentIsRejectedBeforeTouchingTheGeneralLedger() {
        when(customerInvoiceRepository.findById(10L)).thenReturn(Optional.of(postedInvoice));

        assertThrows(IllegalArgumentException.class,
                () -> service.recordReceipt(request(new BigDecimal("999.00"), PaymentMethod.CASH)));

        verifyNoInteractions(glPostingService, customerReceiptRepository);
    }
}
