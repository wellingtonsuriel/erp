package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.account.Account;
import com.pos_onlineshop.hybrid.account.AccountRepository;
import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.currency.CurrencyRepository;
import com.pos_onlineshop.hybrid.cashier.CashierRepository;
import com.pos_onlineshop.hybrid.customerInvoice.CustomerInvoice;
import com.pos_onlineshop.hybrid.customerInvoice.CustomerInvoiceRepository;
import com.pos_onlineshop.hybrid.customerReceipt.CustomerReceipt;
import com.pos_onlineshop.hybrid.customerReceipt.CustomerReceiptRepository;
import com.pos_onlineshop.hybrid.customers.Customers;
import com.pos_onlineshop.hybrid.dtos.RecordCustomerReceiptRequest;
import com.pos_onlineshop.hybrid.enums.AccountType;
import com.pos_onlineshop.hybrid.enums.CustomerInvoiceStatus;
import com.pos_onlineshop.hybrid.enums.DebitCredit;
import com.pos_onlineshop.hybrid.enums.GLSourceModule;
import com.pos_onlineshop.hybrid.enums.PaymentMethod;
import com.pos_onlineshop.hybrid.gl.ManualLineSpec;
import com.pos_onlineshop.hybrid.journalEntry.JournalEntry;
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
class CustomerReceiptServiceTest {

    @Mock private CustomerReceiptRepository customerReceiptRepository;
    @Mock private CustomerInvoiceRepository customerInvoiceRepository;
    @Mock private CurrencyRepository currencyRepository;
    @Mock private CashierRepository cashierRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private GLPostingService glPostingService;
    @Mock private CurrencyService currencyService;

    private CustomerReceiptService service;
    private Currency currency;
    private CustomerInvoice postedInvoice;
    private Account cash;
    private Account cardClearing;
    private Account accountsReceivable;
    private Account fxGainLoss;

    @BeforeEach
    void setUp() {
        service = new CustomerReceiptService(customerReceiptRepository, customerInvoiceRepository,
                currencyRepository, cashierRepository, accountRepository, glPostingService, currencyService);

        currency = Currency.builder().id(1L).code("USD").build();
        Customers customer = Customers.builder().id(1L).name("Wholesale Co").build();
        postedInvoice = CustomerInvoice.builder()
                .id(10L).invoiceNumber("CINV-10").customer(customer)
                .invoiceDate(LocalDate.now()).dueDate(LocalDate.now().plusDays(30))
                .subtotalAmount(new BigDecimal("100.00")).taxAmount(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("100.00")).status(CustomerInvoiceStatus.POSTED)
                .exchangeRate(BigDecimal.ONE)
                .build();
        cash = Account.builder().id(1L).code("1010").name("Cash on Hand")
                .accountType(AccountType.ASSET).normalBalance(DebitCredit.DEBIT).active(true).build();
        cardClearing = Account.builder().id(2L).code("1020").name("Mobile Money / Card Clearing")
                .accountType(AccountType.ASSET).normalBalance(DebitCredit.DEBIT).active(true).build();
        accountsReceivable = Account.builder().id(3L).code("1100").name("Accounts Receivable")
                .accountType(AccountType.ASSET).normalBalance(DebitCredit.DEBIT).controlAccount(true).active(true).build();
        fxGainLoss = Account.builder().id(4L).code("5900").name("FX Gain / Loss")
                .accountType(AccountType.EXPENSE).normalBalance(DebitCredit.DEBIT).active(true).build();

        lenient().when(currencyRepository.findById(1L)).thenReturn(Optional.of(currency));
        lenient().when(currencyService.getBaseCurrency()).thenReturn(currency);
        lenient().when(accountRepository.findByCode("1010")).thenReturn(Optional.of(cash));
        lenient().when(accountRepository.findByCode("1020")).thenReturn(Optional.of(cardClearing));
        lenient().when(accountRepository.findByCode("1100")).thenReturn(Optional.of(accountsReceivable));
        lenient().when(accountRepository.findByCode("5900")).thenReturn(Optional.of(fxGainLoss));
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
    void cashReceiptDebitsCashOnHand() {
        when(customerInvoiceRepository.findById(10L)).thenReturn(Optional.of(postedInvoice));
        JournalEntry entry = JournalEntry.builder().id(500L).entryNumber(50L).build();
        ArgumentCaptor<List<ManualLineSpec>> captor = ArgumentCaptor.forClass(List.class);
        when(glPostingService.postManual(eq("CUSTOMER-RECEIPT-99"), any(LocalDate.class), anyString(),
                eq(GLSourceModule.ORDER), eq("CUSTOMER_RECEIPT"), eq(99L), captor.capture(), anyString()))
                .thenReturn(entry);

        service.recordReceipt(request(new BigDecimal("40.00"), PaymentMethod.CASH));

        assertTrue(captor.getValue().stream().anyMatch(s -> s.account() == cash && s.debitAmount().compareTo(BigDecimal.ZERO) > 0));
        assertTrue(captor.getValue().stream().noneMatch(s -> s.account() == cardClearing));
    }

    @Test
    void nonCashReceiptDebitsCardClearing() {
        when(customerInvoiceRepository.findById(10L)).thenReturn(Optional.of(postedInvoice));
        JournalEntry entry = JournalEntry.builder().id(500L).entryNumber(50L).build();
        ArgumentCaptor<List<ManualLineSpec>> captor = ArgumentCaptor.forClass(List.class);
        when(glPostingService.postManual(anyString(), any(LocalDate.class), anyString(),
                eq(GLSourceModule.ORDER), eq("CUSTOMER_RECEIPT"), eq(99L), captor.capture(), anyString()))
                .thenReturn(entry);

        service.recordReceipt(request(new BigDecimal("40.00"), PaymentMethod.ECOCASH));

        assertTrue(captor.getValue().stream().anyMatch(s -> s.account() == cardClearing));
    }

    @Test
    void fullReceiptMarksInvoicePaidAndUpdatesTheRepository() {
        when(customerInvoiceRepository.findById(10L)).thenReturn(Optional.of(postedInvoice));
        when(glPostingService.postManual(anyString(), any(LocalDate.class), anyString(), any(), anyString(), anyLong(), anyList(), anyString()))
                .thenReturn(JournalEntry.builder().id(500L).entryNumber(50L).build());

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

    @Test
    void sameCurrencyReceiptPostsNoFxLineWhenRatesMatch() {
        when(customerInvoiceRepository.findById(10L)).thenReturn(Optional.of(postedInvoice));
        JournalEntry entry = JournalEntry.builder().id(500L).entryNumber(50L).build();
        ArgumentCaptor<List<ManualLineSpec>> captor = ArgumentCaptor.forClass(List.class);
        when(glPostingService.postManual(anyString(), any(LocalDate.class), anyString(),
                any(), anyString(), anyLong(), captor.capture(), anyString())).thenReturn(entry);

        service.recordReceipt(request(new BigDecimal("40.00"), PaymentMethod.CASH));

        assertEquals(2, captor.getValue().size());
        assertTrue(captor.getValue().stream().noneMatch(s -> s.account() == fxGainLoss));
    }

    @Test
    void receiptSettledAtAHigherRateThanTheInvoiceWasBookedAtRecognizesARealizedGain() {
        postedInvoice.setExchangeRate(new BigDecimal("1.00")); // AR booked at 1:1
        when(customerInvoiceRepository.findById(10L)).thenReturn(Optional.of(postedInvoice));
        Currency zwg = Currency.builder().id(2L).code("ZWG").build();
        when(currencyRepository.findById(2L)).thenReturn(Optional.of(zwg));
        when(currencyService.getExchangeRate(zwg, currency)).thenReturn(new BigDecimal("1.10")); // settled at a better rate
        JournalEntry entry = JournalEntry.builder().id(500L).entryNumber(50L).build();
        ArgumentCaptor<List<ManualLineSpec>> captor = ArgumentCaptor.forClass(List.class);
        when(glPostingService.postManual(anyString(), any(LocalDate.class), anyString(),
                any(), anyString(), anyLong(), captor.capture(), anyString())).thenReturn(entry);

        RecordCustomerReceiptRequest request = request(new BigDecimal("40.00"), PaymentMethod.CASH);
        request.setCurrencyId(2L);
        service.recordReceipt(request);

        List<ManualLineSpec> specs = captor.getValue();
        assertEquals(3, specs.size());
        ManualLineSpec fxLine = specs.stream().filter(s -> s.account() == fxGainLoss).findFirst().orElseThrow();
        // 40 * 1.10 = 44.00 received vs 40 * 1.00 = 40.00 AR value -> 4.00 gain, credited
        assertEquals(0, new BigDecimal("4.0000").compareTo(fxLine.creditAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(fxLine.debitAmount()));
    }

    @Test
    void receiptSettledAtALowerRateThanTheInvoiceWasBookedAtRecognizesARealizedLoss() {
        postedInvoice.setExchangeRate(new BigDecimal("1.10")); // AR booked at the higher rate
        when(customerInvoiceRepository.findById(10L)).thenReturn(Optional.of(postedInvoice));
        Currency zwg = Currency.builder().id(2L).code("ZWG").build();
        when(currencyRepository.findById(2L)).thenReturn(Optional.of(zwg));
        when(currencyService.getExchangeRate(zwg, currency)).thenReturn(new BigDecimal("1.00")); // settled at a worse rate
        JournalEntry entry = JournalEntry.builder().id(500L).entryNumber(50L).build();
        ArgumentCaptor<List<ManualLineSpec>> captor = ArgumentCaptor.forClass(List.class);
        when(glPostingService.postManual(anyString(), any(LocalDate.class), anyString(),
                any(), anyString(), anyLong(), captor.capture(), anyString())).thenReturn(entry);

        RecordCustomerReceiptRequest request = request(new BigDecimal("40.00"), PaymentMethod.CASH);
        request.setCurrencyId(2L);
        service.recordReceipt(request);

        List<ManualLineSpec> specs = captor.getValue();
        ManualLineSpec fxLine = specs.stream().filter(s -> s.account() == fxGainLoss).findFirst().orElseThrow();
        // 40 * 1.00 = 40.00 received vs 40 * 1.10 = 44.00 AR value -> 4.00 loss, debited
        assertEquals(0, new BigDecimal("4.0000").compareTo(fxLine.debitAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(fxLine.creditAmount()));
    }
}
