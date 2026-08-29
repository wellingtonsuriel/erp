package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.account.Account;
import com.pos_onlineshop.hybrid.account.AccountRepository;
import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.customerInvoice.CustomerInvoice;
import com.pos_onlineshop.hybrid.customerInvoice.CustomerInvoiceRepository;
import com.pos_onlineshop.hybrid.customers.Customers;
import com.pos_onlineshop.hybrid.dtos.FxRevaluationResponse;
import com.pos_onlineshop.hybrid.enums.AccountType;
import com.pos_onlineshop.hybrid.enums.CustomerInvoiceStatus;
import com.pos_onlineshop.hybrid.enums.DebitCredit;
import com.pos_onlineshop.hybrid.enums.GLSourceModule;
import com.pos_onlineshop.hybrid.enums.SupplierInvoiceStatus;
import com.pos_onlineshop.hybrid.fxRevaluation.FxRevaluationEntry;
import com.pos_onlineshop.hybrid.fxRevaluation.FxRevaluationEntryRepository;
import com.pos_onlineshop.hybrid.gl.ManualLineSpec;
import com.pos_onlineshop.hybrid.journalEntry.JournalEntry;
import com.pos_onlineshop.hybrid.suppliers.Suppliers;
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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FxRevaluationServiceTest {

    @Mock private CustomerInvoiceRepository customerInvoiceRepository;
    @Mock private SupplierInvoiceRepository supplierInvoiceRepository;
    @Mock private FxRevaluationEntryRepository fxRevaluationEntryRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private GLPostingService glPostingService;
    @Mock private CurrencyService currencyService;

    private FxRevaluationService service;

    private Currency base;
    private Currency zwg;
    private Account accountsReceivable;
    private Account accountsPayable;
    private Account fxGainLoss;
    private final LocalDate revalDate = LocalDate.of(2026, 8, 31);

    @BeforeEach
    void setUp() {
        service = new FxRevaluationService(customerInvoiceRepository, supplierInvoiceRepository,
                fxRevaluationEntryRepository, accountRepository, glPostingService, currencyService);

        base = Currency.builder().id(1L).code("USD").build();
        zwg = Currency.builder().id(2L).code("ZWG").build();
        accountsReceivable = Account.builder().id(1L).code("1100").name("Accounts Receivable")
                .accountType(AccountType.ASSET).normalBalance(DebitCredit.DEBIT).controlAccount(true).active(true).build();
        accountsPayable = Account.builder().id(2L).code("2100").name("Accounts Payable")
                .accountType(AccountType.LIABILITY).normalBalance(DebitCredit.CREDIT).controlAccount(true).active(true).build();
        fxGainLoss = Account.builder().id(3L).code("5900").name("FX Gain / Loss")
                .accountType(AccountType.EXPENSE).normalBalance(DebitCredit.DEBIT).active(true).build();

        lenient().when(currencyService.getBaseCurrency()).thenReturn(base);
        lenient().when(accountRepository.findByCode("1100")).thenReturn(Optional.of(accountsReceivable));
        lenient().when(accountRepository.findByCode("2100")).thenReturn(Optional.of(accountsPayable));
        lenient().when(accountRepository.findByCode("5900")).thenReturn(Optional.of(fxGainLoss));
        lenient().when(customerInvoiceRepository.save(any(CustomerInvoice.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(supplierInvoiceRepository.save(any(SupplierInvoice.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(fxRevaluationEntryRepository.save(any(FxRevaluationEntry.class))).thenAnswer(inv -> {
            FxRevaluationEntry e = inv.getArgument(0);
            if (e.getId() == null) e.setId(500L);
            return e;
        });
        lenient().when(supplierInvoiceRepository.findByStatusIn(anyList())).thenReturn(List.of());
        lenient().when(customerInvoiceRepository.findByStatusIn(anyList())).thenReturn(List.of());
    }

    private CustomerInvoice customerInvoice(BigDecimal total, BigDecimal paid, BigDecimal bookedRate) {
        return CustomerInvoice.builder().id(10L).invoiceNumber("CINV-10")
                .customer(Customers.builder().id(1L).name("Wholesale Co").build())
                .invoiceDate(LocalDate.now()).dueDate(LocalDate.now().plusDays(30))
                .subtotalAmount(total).taxAmount(BigDecimal.ZERO).totalAmount(total).amountPaid(paid)
                .status(CustomerInvoiceStatus.PARTIALLY_PAID).currency(zwg).exchangeRate(bookedRate).build();
    }

    private SupplierInvoice supplierInvoice(BigDecimal total, BigDecimal paid, BigDecimal bookedRate) {
        return SupplierInvoice.builder().id(20L).invoiceNumber("INV-20")
                .supplier(Suppliers.builder().id(1L).name("Acme Supplies").build())
                .invoiceDate(LocalDate.now()).dueDate(LocalDate.now().plusDays(30))
                .subtotalAmount(total).taxAmount(BigDecimal.ZERO).totalAmount(total).amountPaid(paid)
                .status(SupplierInvoiceStatus.PARTIALLY_PAID).currency(zwg).exchangeRate(bookedRate).build();
    }

    @Test
    void customerInvoiceRevaluedUpwardRecognizesAGainAndDebitsAr() {
        CustomerInvoice invoice = customerInvoice(new BigDecimal("100.00"), BigDecimal.ZERO, new BigDecimal("1.00"));
        when(customerInvoiceRepository.findByStatusIn(anyList())).thenReturn(List.of(invoice));
        when(currencyService.getExchangeRate(zwg, base)).thenReturn(new BigDecimal("1.10"));
        JournalEntry entry = JournalEntry.builder().id(900L).entryNumber(90L).build();
        ArgumentCaptor<List<ManualLineSpec>> captor = ArgumentCaptor.forClass(List.class);
        when(glPostingService.postManual(eq("FX-REVAL-CUSTOMER-10-" + revalDate), eq(revalDate), anyString(),
                eq(GLSourceModule.SYSTEM), eq("FX_REVALUATION"), eq(10L), captor.capture(), eq("admin1")))
                .thenReturn(entry);

        List<FxRevaluationResponse> results = service.revalueOpenBalances(revalDate, "admin1");

        assertEquals(1, results.size());
        assertEquals(0, new BigDecimal("10.0000").compareTo(results.get(0).getUnrealizedGainLoss()));
        assertEquals(0, new BigDecimal("1.10").compareTo(invoice.getExchangeRate()));
        List<ManualLineSpec> specs = captor.getValue();
        ManualLineSpec arLine = specs.stream().filter(s -> s.account() == accountsReceivable).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("10.0000").compareTo(arLine.debitAmount()));
        ManualLineSpec fxLine = specs.stream().filter(s -> s.account() == fxGainLoss).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("10.0000").compareTo(fxLine.creditAmount()));
    }

    @Test
    void customerInvoiceRevaluedDownwardRecognizesALossAndCreditsAr() {
        CustomerInvoice invoice = customerInvoice(new BigDecimal("100.00"), BigDecimal.ZERO, new BigDecimal("1.10"));
        when(customerInvoiceRepository.findByStatusIn(anyList())).thenReturn(List.of(invoice));
        when(currencyService.getExchangeRate(zwg, base)).thenReturn(new BigDecimal("1.00"));
        when(glPostingService.postManual(anyString(), eq(revalDate), anyString(), any(), anyString(), anyLong(), anyList(), anyString()))
                .thenReturn(JournalEntry.builder().id(900L).entryNumber(90L).build());

        List<FxRevaluationResponse> results = service.revalueOpenBalances(revalDate, "admin1");

        assertEquals(1, results.size());
        assertEquals(0, new BigDecimal("-10.0000").compareTo(results.get(0).getUnrealizedGainLoss()));
    }

    @Test
    void customerInvoiceWithNoRateMovementIsSkipped() {
        CustomerInvoice invoice = customerInvoice(new BigDecimal("100.00"), BigDecimal.ZERO, new BigDecimal("1.10"));
        when(customerInvoiceRepository.findByStatusIn(anyList())).thenReturn(List.of(invoice));
        when(currencyService.getExchangeRate(zwg, base)).thenReturn(new BigDecimal("1.10"));

        List<FxRevaluationResponse> results = service.revalueOpenBalances(revalDate, "admin1");

        assertTrue(results.isEmpty());
        verifyNoInteractions(glPostingService);
        verify(customerInvoiceRepository, never()).save(any());
    }

    @Test
    void sameCurrencyInvoiceIsSkippedEntirely() {
        CustomerInvoice invoice = CustomerInvoice.builder().id(11L).invoiceNumber("CINV-11")
                .customer(Customers.builder().id(1L).name("Local Co").build())
                .invoiceDate(LocalDate.now()).dueDate(LocalDate.now().plusDays(30))
                .subtotalAmount(new BigDecimal("100.00")).taxAmount(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("100.00")).amountPaid(BigDecimal.ZERO)
                .status(CustomerInvoiceStatus.PARTIALLY_PAID).currency(base).exchangeRate(BigDecimal.ONE).build();
        when(customerInvoiceRepository.findByStatusIn(anyList())).thenReturn(List.of(invoice));

        List<FxRevaluationResponse> results = service.revalueOpenBalances(revalDate, "admin1");

        assertTrue(results.isEmpty());
        verifyNoInteractions(glPostingService, accountRepository);
    }

    @Test
    void supplierInvoiceRevaluedUpwardRecognizesALossAndCreditsAp() {
        SupplierInvoice invoice = supplierInvoice(new BigDecimal("100.00"), BigDecimal.ZERO, new BigDecimal("1.00"));
        when(supplierInvoiceRepository.findByStatusIn(anyList())).thenReturn(List.of(invoice));
        when(currencyService.getExchangeRate(zwg, base)).thenReturn(new BigDecimal("1.10"));
        JournalEntry entry = JournalEntry.builder().id(901L).entryNumber(91L).build();
        ArgumentCaptor<List<ManualLineSpec>> captor = ArgumentCaptor.forClass(List.class);
        when(glPostingService.postManual(eq("FX-REVAL-SUPPLIER-20-" + revalDate), eq(revalDate), anyString(),
                eq(GLSourceModule.SYSTEM), eq("FX_REVALUATION"), eq(20L), captor.capture(), eq("admin1")))
                .thenReturn(entry);

        List<FxRevaluationResponse> results = service.revalueOpenBalances(revalDate, "admin1");

        assertEquals(1, results.size());
        // Liability worth more now = a loss, even though the raw diff is positive.
        assertEquals(0, new BigDecimal("-10.0000").compareTo(results.get(0).getUnrealizedGainLoss()));
        ManualLineSpec apLine = captor.getValue().stream().filter(s -> s.account() == accountsPayable).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("10.0000").compareTo(apLine.creditAmount()));
        ManualLineSpec fxLine = captor.getValue().stream().filter(s -> s.account() == fxGainLoss).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("10.0000").compareTo(fxLine.debitAmount()));
    }

    @Test
    void supplierInvoiceRevaluedDownwardRecognizesAGainAndDebitsAp() {
        SupplierInvoice invoice = supplierInvoice(new BigDecimal("100.00"), BigDecimal.ZERO, new BigDecimal("1.10"));
        when(supplierInvoiceRepository.findByStatusIn(anyList())).thenReturn(List.of(invoice));
        when(currencyService.getExchangeRate(zwg, base)).thenReturn(new BigDecimal("1.00"));
        when(glPostingService.postManual(anyString(), eq(revalDate), anyString(), any(), anyString(), anyLong(), anyList(), anyString()))
                .thenReturn(JournalEntry.builder().id(901L).entryNumber(91L).build());

        List<FxRevaluationResponse> results = service.revalueOpenBalances(revalDate, "admin1");

        assertEquals(1, results.size());
        assertEquals(0, new BigDecimal("10.0000").compareTo(results.get(0).getUnrealizedGainLoss()));
    }

    @Test
    void fullyPaidInvoiceIsSkippedSinceThereIsNoOutstandingBalanceToRevalue() {
        CustomerInvoice invoice = customerInvoice(new BigDecimal("100.00"), new BigDecimal("100.00"), new BigDecimal("1.00"));
        when(customerInvoiceRepository.findByStatusIn(anyList())).thenReturn(List.of(invoice));

        List<FxRevaluationResponse> results = service.revalueOpenBalances(revalDate, "admin1");

        assertTrue(results.isEmpty());
        verifyNoInteractions(glPostingService);
    }
}
