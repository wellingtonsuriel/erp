package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.account.Account;
import com.pos_onlineshop.hybrid.account.AccountRepository;
import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.currency.CurrencyRepository;
import com.pos_onlineshop.hybrid.enums.AccountType;
import com.pos_onlineshop.hybrid.enums.DebitCredit;
import com.pos_onlineshop.hybrid.enums.DebitNoteStatus;
import com.pos_onlineshop.hybrid.enums.GLSourceModule;
import com.pos_onlineshop.hybrid.enums.SupplierInvoiceStatus;
import com.pos_onlineshop.hybrid.dtos.CreateSupplierDebitNoteRequest;
import com.pos_onlineshop.hybrid.dtos.SupplierDebitNoteResponse;
import com.pos_onlineshop.hybrid.gl.ManualLineSpec;
import com.pos_onlineshop.hybrid.journalEntry.JournalEntry;
import com.pos_onlineshop.hybrid.purchaseOrder.PurchaseOrder;
import com.pos_onlineshop.hybrid.suppliers.Suppliers;
import com.pos_onlineshop.hybrid.supplierDebitNote.SupplierDebitNote;
import com.pos_onlineshop.hybrid.supplierDebitNote.SupplierDebitNoteRepository;
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
class SupplierDebitNoteServiceTest {

    @Mock private SupplierDebitNoteRepository supplierDebitNoteRepository;
    @Mock private SupplierInvoiceRepository supplierInvoiceRepository;
    @Mock private CurrencyRepository currencyRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private GLPostingService glPostingService;
    @Mock private CurrencyService currencyService;

    private SupplierDebitNoteService service;

    private Currency currency;
    private Suppliers supplier;
    private Account accountsPayable;
    private Account inventory;
    private Account operatingExpenses;

    @BeforeEach
    void setUp() {
        service = new SupplierDebitNoteService(supplierDebitNoteRepository, supplierInvoiceRepository,
                currencyRepository, accountRepository, glPostingService, currencyService);

        currency = Currency.builder().id(1L).code("USD").build();
        supplier = Suppliers.builder().id(1L).name("Acme Supplies").build();
        accountsPayable = Account.builder().id(1L).code("2100").name("Accounts Payable")
                .accountType(AccountType.LIABILITY).normalBalance(DebitCredit.CREDIT).controlAccount(true).active(true).build();
        inventory = Account.builder().id(2L).code("1200").name("Inventory Asset")
                .accountType(AccountType.ASSET).normalBalance(DebitCredit.DEBIT).controlAccount(true).active(true).build();
        operatingExpenses = Account.builder().id(3L).code("5300").name("Operating Expenses")
                .accountType(AccountType.EXPENSE).normalBalance(DebitCredit.DEBIT).active(true).build();

        lenient().when(currencyRepository.findById(1L)).thenReturn(Optional.of(currency));
        lenient().when(currencyService.getBaseCurrency()).thenReturn(currency);
        lenient().when(accountRepository.findByCode("2100")).thenReturn(Optional.of(accountsPayable));
        lenient().when(accountRepository.findByCode("1200")).thenReturn(Optional.of(inventory));
        lenient().when(accountRepository.findByCode("5300")).thenReturn(Optional.of(operatingExpenses));
    }

    private SupplierInvoice invoice(boolean poLinked) {
        return SupplierInvoice.builder()
                .id(10L).invoiceNumber("INV-10").supplier(supplier)
                .invoiceDate(LocalDate.now()).dueDate(LocalDate.now().plusDays(30))
                .subtotalAmount(new BigDecimal("100.00")).taxAmount(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("100.00")).status(SupplierInvoiceStatus.POSTED)
                .purchaseOrder(poLinked ? PurchaseOrder.builder().id(5L).poNumber("PO-5").build() : null)
                .build();
    }

    private CreateSupplierDebitNoteRequest request(BigDecimal amount) {
        CreateSupplierDebitNoteRequest request = new CreateSupplierDebitNoteRequest();
        request.setDebitNoteNumber("DN-1");
        request.setInvoiceId(10L);
        request.setCurrencyId(1L);
        request.setAmount(amount);
        request.setReason("Goods returned to supplier");
        request.setIssueDate(LocalDate.now());
        return request;
    }

    @Test
    void createDebitNoteRejectsADuplicateNumber() {
        when(supplierDebitNoteRepository.existsByDebitNoteNumber("DN-1")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> service.createDebitNote(request(new BigDecimal("40.00"))));
        verifyNoInteractions(supplierInvoiceRepository);
    }

    @Test
    void createDebitNoteRejectsAnAmountExceedingTheOutstandingBalance() {
        when(supplierDebitNoteRepository.existsByDebitNoteNumber("DN-1")).thenReturn(false);
        when(supplierInvoiceRepository.findById(10L)).thenReturn(Optional.of(invoice(false)));

        assertThrows(IllegalArgumentException.class, () -> service.createDebitNote(request(new BigDecimal("150.00"))));
        verify(supplierDebitNoteRepository, never()).save(any());
    }

    private SupplierDebitNote draftDebitNote(BigDecimal amount, SupplierInvoice inv) {
        return SupplierDebitNote.builder().id(50L).debitNoteNumber("DN-1")
                .supplier(supplier).invoice(inv).currency(currency)
                .amount(amount).reason("Goods returned to supplier").issueDate(LocalDate.now())
                .status(DebitNoteStatus.DRAFT).build();
    }

    @Test
    void postDebitNoteCreditsInventoryForAPoLinkedInvoice() {
        SupplierInvoice inv = invoice(true);
        SupplierDebitNote debitNote = draftDebitNote(new BigDecimal("40.00"), inv);
        when(supplierDebitNoteRepository.findById(50L)).thenReturn(Optional.of(debitNote));
        when(supplierInvoiceRepository.save(any(SupplierInvoice.class))).thenAnswer(inv2 -> inv2.getArgument(0));
        JournalEntry entry = JournalEntry.builder().id(200L).entryNumber(80L).build();
        ArgumentCaptor<List<ManualLineSpec>> captor = ArgumentCaptor.forClass(List.class);
        when(glPostingService.postManual(eq("SUPPLIER-DEBIT-NOTE-50"), any(LocalDate.class), anyString(),
                eq(GLSourceModule.INVENTORY), eq("SUPPLIER_DEBIT_NOTE"), eq(50L), captor.capture(), eq("system")))
                .thenReturn(entry);
        when(supplierDebitNoteRepository.save(any(SupplierDebitNote.class))).thenAnswer(inv2 -> inv2.getArgument(0));

        SupplierDebitNoteResponse response = service.postDebitNote(50L);

        assertEquals("POSTED", response.getStatus());
        assertEquals(0, new BigDecimal("40.00").compareTo(inv.getAmountPaid()));
        List<ManualLineSpec> specs = captor.getValue();
        assertEquals(2, specs.size());
        assertTrue(specs.stream().anyMatch(s -> s.account() == accountsPayable && s.debitAmount().compareTo(BigDecimal.ZERO) > 0));
        assertTrue(specs.stream().anyMatch(s -> s.account() == inventory && s.creditAmount().compareTo(BigDecimal.ZERO) > 0));
        assertTrue(specs.stream().noneMatch(s -> s.account() == operatingExpenses));
    }

    @Test
    void postDebitNoteCreditsOperatingExpensesForAStandaloneInvoice() {
        SupplierInvoice inv = invoice(false);
        SupplierDebitNote debitNote = draftDebitNote(new BigDecimal("40.00"), inv);
        when(supplierDebitNoteRepository.findById(50L)).thenReturn(Optional.of(debitNote));
        when(supplierInvoiceRepository.save(any(SupplierInvoice.class))).thenAnswer(inv2 -> inv2.getArgument(0));
        JournalEntry entry = JournalEntry.builder().id(201L).entryNumber(81L).build();
        ArgumentCaptor<List<ManualLineSpec>> captor = ArgumentCaptor.forClass(List.class);
        when(glPostingService.postManual(eq("SUPPLIER-DEBIT-NOTE-50"), any(LocalDate.class), anyString(),
                eq(GLSourceModule.INVENTORY), eq("SUPPLIER_DEBIT_NOTE"), eq(50L), captor.capture(), eq("system")))
                .thenReturn(entry);
        when(supplierDebitNoteRepository.save(any(SupplierDebitNote.class))).thenAnswer(inv2 -> inv2.getArgument(0));

        service.postDebitNote(50L);

        List<ManualLineSpec> specs = captor.getValue();
        assertTrue(specs.stream().anyMatch(s -> s.account() == operatingExpenses && s.creditAmount().compareTo(BigDecimal.ZERO) > 0));
        assertTrue(specs.stream().noneMatch(s -> s.account() == inventory));
    }

    @Test
    void postDebitNoteRejectsAnAlreadyPostedDebitNote() {
        SupplierDebitNote debitNote = draftDebitNote(new BigDecimal("40.00"), invoice(false));
        debitNote.setStatus(DebitNoteStatus.POSTED);
        when(supplierDebitNoteRepository.findById(50L)).thenReturn(Optional.of(debitNote));

        assertThrows(IllegalStateException.class, () -> service.postDebitNote(50L));
        verifyNoInteractions(glPostingService);
    }

    @Test
    void voidDebitNoteRejectsAPostedDebitNote() {
        SupplierDebitNote debitNote = draftDebitNote(new BigDecimal("40.00"), invoice(false));
        debitNote.setStatus(DebitNoteStatus.POSTED);
        when(supplierDebitNoteRepository.findById(50L)).thenReturn(Optional.of(debitNote));

        assertThrows(IllegalStateException.class, () -> service.voidDebitNote(50L, "changed mind"));
    }

    @Test
    void voidDebitNoteVoidsADraft() {
        SupplierDebitNote debitNote = draftDebitNote(new BigDecimal("40.00"), invoice(false));
        when(supplierDebitNoteRepository.findById(50L)).thenReturn(Optional.of(debitNote));
        when(supplierDebitNoteRepository.save(any(SupplierDebitNote.class))).thenAnswer(inv -> inv.getArgument(0));

        SupplierDebitNoteResponse response = service.voidDebitNote(50L, "changed mind");

        assertEquals("VOID", response.getStatus());
    }
}
