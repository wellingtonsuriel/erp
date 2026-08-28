package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.currency.CurrencyRepository;
import com.pos_onlineshop.hybrid.dtos.RecordSupplierPaymentRequest;
import com.pos_onlineshop.hybrid.enums.FinancialEventType;
import com.pos_onlineshop.hybrid.enums.PaymentMethod;
import com.pos_onlineshop.hybrid.enums.SupplierInvoiceStatus;
import com.pos_onlineshop.hybrid.cashier.CashierRepository;
import com.pos_onlineshop.hybrid.gl.FinancialEvent;
import com.pos_onlineshop.hybrid.suppliers.Suppliers;
import com.pos_onlineshop.hybrid.supplierInvoice.SupplierInvoice;
import com.pos_onlineshop.hybrid.supplierInvoice.SupplierInvoiceRepository;
import com.pos_onlineshop.hybrid.supplierPayment.SupplierPayment;
import com.pos_onlineshop.hybrid.supplierPayment.SupplierPaymentRepository;
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
class SupplierPaymentServiceTest {

    @Mock private SupplierPaymentRepository supplierPaymentRepository;
    @Mock private SupplierInvoiceRepository supplierInvoiceRepository;
    @Mock private CurrencyRepository currencyRepository;
    @Mock private CashierRepository cashierRepository;
    @Mock private GLPostingService glPostingService;
    @Mock private CurrencyService currencyService;

    private SupplierPaymentService service;
    private Currency currency;
    private SupplierInvoice postedInvoice;

    @BeforeEach
    void setUp() {
        service = new SupplierPaymentService(supplierPaymentRepository, supplierInvoiceRepository,
                currencyRepository, cashierRepository, glPostingService, currencyService);

        currency = Currency.builder().id(1L).code("USD").build();
        Suppliers supplier = Suppliers.builder().id(1L).name("Acme Supplies").build();
        postedInvoice = SupplierInvoice.builder()
                .id(10L).invoiceNumber("INV-10").supplier(supplier)
                .invoiceDate(LocalDate.now()).dueDate(LocalDate.now().plusDays(30))
                .subtotalAmount(new BigDecimal("100.00")).taxAmount(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("100.00")).status(SupplierInvoiceStatus.POSTED)
                .build();

        lenient().when(currencyRepository.findById(1L)).thenReturn(Optional.of(currency));
        lenient().when(currencyService.getBaseCurrency()).thenReturn(currency);
        lenient().when(supplierPaymentRepository.save(any(SupplierPayment.class))).thenAnswer(inv -> {
            SupplierPayment p = inv.getArgument(0);
            p.setId(99L);
            return p;
        });
    }

    private RecordSupplierPaymentRequest request(BigDecimal amount, PaymentMethod method) {
        RecordSupplierPaymentRequest request = new RecordSupplierPaymentRequest();
        request.setInvoiceId(10L);
        request.setAmount(amount);
        request.setCurrencyId(1L);
        request.setPaymentMethod(method);
        return request;
    }

    @Test
    void cashPaymentPostsSupplierPaymentCashEventType() {
        when(supplierInvoiceRepository.findById(10L)).thenReturn(Optional.of(postedInvoice));

        service.recordPayment(request(new BigDecimal("40.00"), PaymentMethod.CASH));

        ArgumentCaptor<FinancialEvent> captor = ArgumentCaptor.forClass(FinancialEvent.class);
        verify(glPostingService).post(captor.capture());
        assertEquals(FinancialEventType.SUPPLIER_PAYMENT_CASH, captor.getValue().getEventType());
        assertEquals("SUPPLIER-PAYMENT-99", captor.getValue().getIdempotencyKey());
    }

    @Test
    void bankPaymentPostsSupplierPaymentEventType() {
        when(supplierInvoiceRepository.findById(10L)).thenReturn(Optional.of(postedInvoice));

        service.recordPayment(request(new BigDecimal("40.00"), PaymentMethod.ONLINE_PAYMENT));

        ArgumentCaptor<FinancialEvent> captor = ArgumentCaptor.forClass(FinancialEvent.class);
        verify(glPostingService).post(captor.capture());
        assertEquals(FinancialEventType.SUPPLIER_PAYMENT, captor.getValue().getEventType());
    }

    @Test
    void fullPaymentMarksInvoicePaidAndUpdatesTheRepository() {
        when(supplierInvoiceRepository.findById(10L)).thenReturn(Optional.of(postedInvoice));

        service.recordPayment(request(new BigDecimal("100.00"), PaymentMethod.CASH));

        assertEquals(SupplierInvoiceStatus.PAID, postedInvoice.getStatus());
        verify(supplierInvoiceRepository).save(postedInvoice);
    }

    @Test
    void overpaymentIsRejectedBeforeTouchingTheGeneralLedger() {
        when(supplierInvoiceRepository.findById(10L)).thenReturn(Optional.of(postedInvoice));

        assertThrows(IllegalArgumentException.class,
                () -> service.recordPayment(request(new BigDecimal("999.00"), PaymentMethod.CASH)));

        verifyNoInteractions(glPostingService, supplierPaymentRepository);
    }
}
