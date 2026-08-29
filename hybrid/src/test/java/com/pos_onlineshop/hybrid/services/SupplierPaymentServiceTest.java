package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.account.Account;
import com.pos_onlineshop.hybrid.account.AccountRepository;
import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.currency.CurrencyRepository;
import com.pos_onlineshop.hybrid.dtos.RecordSupplierPaymentRequest;
import com.pos_onlineshop.hybrid.enums.AccountType;
import com.pos_onlineshop.hybrid.enums.DebitCredit;
import com.pos_onlineshop.hybrid.enums.GLSourceModule;
import com.pos_onlineshop.hybrid.enums.PaymentMethod;
import com.pos_onlineshop.hybrid.enums.SupplierInvoiceStatus;
import com.pos_onlineshop.hybrid.cashier.CashierRepository;
import com.pos_onlineshop.hybrid.gl.ManualLineSpec;
import com.pos_onlineshop.hybrid.journalEntry.JournalEntry;
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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SupplierPaymentServiceTest {

    @Mock private SupplierPaymentRepository supplierPaymentRepository;
    @Mock private SupplierInvoiceRepository supplierInvoiceRepository;
    @Mock private CurrencyRepository currencyRepository;
    @Mock private CashierRepository cashierRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private GLPostingService glPostingService;
    @Mock private CurrencyService currencyService;

    private SupplierPaymentService service;
    private Currency currency;
    private SupplierInvoice postedInvoice;
    private Account cash;
    private Account bank;
    private Account accountsPayable;
    private Account fxGainLoss;

    @BeforeEach
    void setUp() {
        service = new SupplierPaymentService(supplierPaymentRepository, supplierInvoiceRepository,
                currencyRepository, cashierRepository, accountRepository, glPostingService, currencyService);

        currency = Currency.builder().id(1L).code("USD").build();
        Suppliers supplier = Suppliers.builder().id(1L).name("Acme Supplies").build();
        postedInvoice = SupplierInvoice.builder()
                .id(10L).invoiceNumber("INV-10").supplier(supplier)
                .invoiceDate(LocalDate.now()).dueDate(LocalDate.now().plusDays(30))
                .subtotalAmount(new BigDecimal("100.00")).taxAmount(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("100.00")).status(SupplierInvoiceStatus.POSTED)
                .exchangeRate(BigDecimal.ONE)
                .build();
        cash = Account.builder().id(1L).code("1010").name("Cash on Hand")
                .accountType(AccountType.ASSET).normalBalance(DebitCredit.DEBIT).active(true).build();
        bank = Account.builder().id(2L).code("1030").name("Bank")
                .accountType(AccountType.ASSET).normalBalance(DebitCredit.DEBIT).active(true).build();
        accountsPayable = Account.builder().id(3L).code("2100").name("Accounts Payable")
                .accountType(AccountType.LIABILITY).normalBalance(DebitCredit.CREDIT).controlAccount(true).active(true).build();
        fxGainLoss = Account.builder().id(4L).code("5900").name("FX Gain / Loss")
                .accountType(AccountType.EXPENSE).normalBalance(DebitCredit.DEBIT).active(true).build();

        lenient().when(currencyRepository.findById(1L)).thenReturn(Optional.of(currency));
        lenient().when(currencyService.getBaseCurrency()).thenReturn(currency);
        lenient().when(accountRepository.findByCode("1010")).thenReturn(Optional.of(cash));
        lenient().when(accountRepository.findByCode("1030")).thenReturn(Optional.of(bank));
        lenient().when(accountRepository.findByCode("2100")).thenReturn(Optional.of(accountsPayable));
        lenient().when(accountRepository.findByCode("5900")).thenReturn(Optional.of(fxGainLoss));
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
    void cashPaymentCreditsCashOnHand() {
        when(supplierInvoiceRepository.findById(10L)).thenReturn(Optional.of(postedInvoice));
        JournalEntry entry = JournalEntry.builder().id(500L).entryNumber(50L).build();
        ArgumentCaptor<List<ManualLineSpec>> captor = ArgumentCaptor.forClass(List.class);
        when(glPostingService.postManual(eq("SUPPLIER-PAYMENT-99"), any(LocalDate.class), anyString(),
                eq(GLSourceModule.INVENTORY), eq("SUPPLIER_PAYMENT"), eq(99L), captor.capture(), anyString()))
                .thenReturn(entry);

        service.recordPayment(request(new BigDecimal("40.00"), PaymentMethod.CASH));

        assertTrue(captor.getValue().stream().anyMatch(s -> s.account() == cash && s.creditAmount().compareTo(BigDecimal.ZERO) > 0));
        assertTrue(captor.getValue().stream().noneMatch(s -> s.account() == bank));
    }

    @Test
    void bankPaymentCreditsBank() {
        when(supplierInvoiceRepository.findById(10L)).thenReturn(Optional.of(postedInvoice));
        JournalEntry entry = JournalEntry.builder().id(500L).entryNumber(50L).build();
        ArgumentCaptor<List<ManualLineSpec>> captor = ArgumentCaptor.forClass(List.class);
        when(glPostingService.postManual(anyString(), any(LocalDate.class), anyString(),
                eq(GLSourceModule.INVENTORY), eq("SUPPLIER_PAYMENT"), eq(99L), captor.capture(), anyString()))
                .thenReturn(entry);

        service.recordPayment(request(new BigDecimal("40.00"), PaymentMethod.ONLINE_PAYMENT));

        assertTrue(captor.getValue().stream().anyMatch(s -> s.account() == bank));
    }

    @Test
    void fullPaymentMarksInvoicePaidAndUpdatesTheRepository() {
        when(supplierInvoiceRepository.findById(10L)).thenReturn(Optional.of(postedInvoice));
        when(glPostingService.postManual(anyString(), any(LocalDate.class), anyString(), any(), anyString(), anyLong(), anyList(), anyString()))
                .thenReturn(JournalEntry.builder().id(500L).entryNumber(50L).build());

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

    @Test
    void paymentSettledAtALowerRateThanTheInvoiceWasBookedAtRecognizesARealizedGain() {
        postedInvoice.setExchangeRate(new BigDecimal("1.10")); // AP booked at the higher rate
        when(supplierInvoiceRepository.findById(10L)).thenReturn(Optional.of(postedInvoice));
        Currency zwg = Currency.builder().id(2L).code("ZWG").build();
        when(currencyRepository.findById(2L)).thenReturn(Optional.of(zwg));
        when(currencyService.getExchangeRate(zwg, currency)).thenReturn(new BigDecimal("1.00")); // paid at a better (lower) rate
        JournalEntry entry = JournalEntry.builder().id(500L).entryNumber(50L).build();
        ArgumentCaptor<List<ManualLineSpec>> captor = ArgumentCaptor.forClass(List.class);
        when(glPostingService.postManual(anyString(), any(LocalDate.class), anyString(),
                any(), anyString(), anyLong(), captor.capture(), anyString())).thenReturn(entry);

        RecordSupplierPaymentRequest request = request(new BigDecimal("40.00"), PaymentMethod.CASH);
        request.setCurrencyId(2L);
        service.recordPayment(request);

        List<ManualLineSpec> specs = captor.getValue();
        assertEquals(3, specs.size());
        ManualLineSpec fxLine = specs.stream().filter(s -> s.account() == fxGainLoss).findFirst().orElseThrow();
        // AP relieved at 40*1.10=44.00, only 40*1.00=40.00 actually paid -> 4.00 gain, credited
        assertEquals(0, new BigDecimal("4.0000").compareTo(fxLine.creditAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(fxLine.debitAmount()));
    }

    @Test
    void paymentSettledAtAHigherRateThanTheInvoiceWasBookedAtRecognizesARealizedLoss() {
        postedInvoice.setExchangeRate(new BigDecimal("1.00")); // AP booked at 1:1
        when(supplierInvoiceRepository.findById(10L)).thenReturn(Optional.of(postedInvoice));
        Currency zwg = Currency.builder().id(2L).code("ZWG").build();
        when(currencyRepository.findById(2L)).thenReturn(Optional.of(zwg));
        when(currencyService.getExchangeRate(zwg, currency)).thenReturn(new BigDecimal("1.10")); // paid at a worse (higher) rate
        JournalEntry entry = JournalEntry.builder().id(500L).entryNumber(50L).build();
        ArgumentCaptor<List<ManualLineSpec>> captor = ArgumentCaptor.forClass(List.class);
        when(glPostingService.postManual(anyString(), any(LocalDate.class), anyString(),
                any(), anyString(), anyLong(), captor.capture(), anyString())).thenReturn(entry);

        RecordSupplierPaymentRequest request = request(new BigDecimal("40.00"), PaymentMethod.CASH);
        request.setCurrencyId(2L);
        service.recordPayment(request);

        List<ManualLineSpec> specs = captor.getValue();
        ManualLineSpec fxLine = specs.stream().filter(s -> s.account() == fxGainLoss).findFirst().orElseThrow();
        // AP relieved at 40*1.00=40.00, but 40*1.10=44.00 actually paid -> 4.00 loss, debited
        assertEquals(0, new BigDecimal("4.0000").compareTo(fxLine.debitAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(fxLine.creditAmount()));
    }
}
