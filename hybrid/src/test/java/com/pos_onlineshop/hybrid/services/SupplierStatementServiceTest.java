package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.dtos.SupplierStatementReport;
import com.pos_onlineshop.hybrid.enums.DebitNoteStatus;
import com.pos_onlineshop.hybrid.enums.SupplierInvoiceStatus;
import com.pos_onlineshop.hybrid.suppliers.Suppliers;
import com.pos_onlineshop.hybrid.suppliers.SuppliersRepository;
import com.pos_onlineshop.hybrid.supplierDebitNote.SupplierDebitNote;
import com.pos_onlineshop.hybrid.supplierDebitNote.SupplierDebitNoteRepository;
import com.pos_onlineshop.hybrid.supplierInvoice.SupplierInvoice;
import com.pos_onlineshop.hybrid.supplierInvoice.SupplierInvoiceRepository;
import com.pos_onlineshop.hybrid.supplierPayment.SupplierPayment;
import com.pos_onlineshop.hybrid.supplierPayment.SupplierPaymentRepository;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SupplierStatementServiceTest {

    @Mock private SuppliersRepository suppliersRepository;
    @Mock private SupplierInvoiceRepository supplierInvoiceRepository;
    @Mock private SupplierPaymentRepository supplierPaymentRepository;
    @Mock private SupplierDebitNoteRepository supplierDebitNoteRepository;

    private SupplierStatementService service;
    private Suppliers supplier;

    @BeforeEach
    void setUp() {
        service = new SupplierStatementService(suppliersRepository, supplierInvoiceRepository,
                supplierPaymentRepository, supplierDebitNoteRepository);

        supplier = Suppliers.builder().id(1L).name("Acme Supplies").build();
        lenient().when(suppliersRepository.findById(1L)).thenReturn(Optional.of(supplier));
        lenient().when(supplierPaymentRepository.findBySupplier(supplier)).thenReturn(List.of());
        lenient().when(supplierDebitNoteRepository.findBySupplier(supplier)).thenReturn(List.of());
    }

    private SupplierInvoice invoice(String number, LocalDate date, BigDecimal total, SupplierInvoiceStatus status) {
        return SupplierInvoice.builder().id((long) number.hashCode()).invoiceNumber(number).supplier(supplier)
                .invoiceDate(date).dueDate(date.plusDays(30)).subtotalAmount(total).taxAmount(BigDecimal.ZERO)
                .totalAmount(total).amountPaid(BigDecimal.ZERO).status(status).build();
    }

    @Test
    void throwsWhenSupplierNotFound() {
        when(suppliersRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class,
                () -> service.generate(99L, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)));
    }

    @Test
    void invoicePaymentAndDebitNoteWithinRangeProduceARunningBalance() {
        SupplierInvoice inv = invoice("INV-1", LocalDate.of(2026, 8, 5), new BigDecimal("300.00"), SupplierInvoiceStatus.PARTIALLY_PAID);
        when(supplierInvoiceRepository.findBySupplier(supplier)).thenReturn(List.of(inv));

        SupplierPayment payment = SupplierPayment.builder().id(1L).supplier(supplier).invoice(inv)
                .amount(new BigDecimal("100.00")).paymentDate(LocalDate.of(2026, 8, 10)).build();
        when(supplierPaymentRepository.findBySupplier(supplier)).thenReturn(List.of(payment));

        SupplierDebitNote debitNote = SupplierDebitNote.builder().id(1L).debitNoteNumber("DN-1")
                .supplier(supplier).invoice(inv).amount(new BigDecimal("30.00")).reason("Return")
                .issueDate(LocalDate.of(2026, 8, 15)).status(DebitNoteStatus.POSTED).build();
        when(supplierDebitNoteRepository.findBySupplier(supplier)).thenReturn(List.of(debitNote));

        SupplierStatementReport report = service.generate(1L, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        assertEquals(0, BigDecimal.ZERO.compareTo(report.getOpeningBalance()));
        assertEquals(3, report.getLines().size());
        // 300 (invoice) - 100 (payment) - 30 (debit note) = 170
        assertEquals(0, new BigDecimal("170.00").compareTo(report.getClosingBalance()));
    }

    @Test
    void draftDebitNotesAreExcludedEntirely() {
        SupplierInvoice inv = invoice("INV-2", LocalDate.of(2026, 8, 5), new BigDecimal("300.00"), SupplierInvoiceStatus.POSTED);
        when(supplierInvoiceRepository.findBySupplier(supplier)).thenReturn(List.of(inv));
        SupplierDebitNote draftDebitNote = SupplierDebitNote.builder().id(2L).debitNoteNumber("DN-2")
                .supplier(supplier).invoice(inv).amount(new BigDecimal("30.00")).reason("Pending")
                .issueDate(LocalDate.of(2026, 8, 15)).status(DebitNoteStatus.DRAFT).build();
        when(supplierDebitNoteRepository.findBySupplier(supplier)).thenReturn(List.of(draftDebitNote));

        SupplierStatementReport report = service.generate(1L, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        assertEquals(1, report.getLines().size());
        assertEquals("INVOICE", report.getLines().get(0).getType());
    }

    @Test
    void invoicesBeforeTheRangeContributeOnlyToTheOpeningBalance() {
        SupplierInvoice priorInvoice = invoice("INV-3", LocalDate.of(2026, 7, 15), new BigDecimal("150.00"), SupplierInvoiceStatus.POSTED);
        when(supplierInvoiceRepository.findBySupplier(supplier)).thenReturn(List.of(priorInvoice));

        SupplierStatementReport report = service.generate(1L, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        assertEquals(0, new BigDecimal("150.00").compareTo(report.getOpeningBalance()));
        assertTrue(report.getLines().isEmpty());
    }
}
