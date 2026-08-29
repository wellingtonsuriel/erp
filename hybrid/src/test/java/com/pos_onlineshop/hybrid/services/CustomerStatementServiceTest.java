package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.customerCreditNote.CustomerCreditNote;
import com.pos_onlineshop.hybrid.customerCreditNote.CustomerCreditNoteRepository;
import com.pos_onlineshop.hybrid.customerInvoice.CustomerInvoice;
import com.pos_onlineshop.hybrid.customerInvoice.CustomerInvoiceRepository;
import com.pos_onlineshop.hybrid.customerReceipt.CustomerReceipt;
import com.pos_onlineshop.hybrid.customerReceipt.CustomerReceiptRepository;
import com.pos_onlineshop.hybrid.customers.Customers;
import com.pos_onlineshop.hybrid.customers.CustomersRepository;
import com.pos_onlineshop.hybrid.dtos.CustomerStatementReport;
import com.pos_onlineshop.hybrid.enums.CreditNoteStatus;
import com.pos_onlineshop.hybrid.enums.CustomerInvoiceStatus;
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
class CustomerStatementServiceTest {

    @Mock private CustomersRepository customersRepository;
    @Mock private CustomerInvoiceRepository customerInvoiceRepository;
    @Mock private CustomerReceiptRepository customerReceiptRepository;
    @Mock private CustomerCreditNoteRepository customerCreditNoteRepository;

    private CustomerStatementService service;
    private Customers customer;

    @BeforeEach
    void setUp() {
        service = new CustomerStatementService(customersRepository, customerInvoiceRepository,
                customerReceiptRepository, customerCreditNoteRepository);

        customer = Customers.builder().id(1L).name("Wholesale Co").build();
        lenient().when(customersRepository.findById(1L)).thenReturn(Optional.of(customer));
        lenient().when(customerReceiptRepository.findByCustomer(customer)).thenReturn(List.of());
        lenient().when(customerCreditNoteRepository.findByCustomer(customer)).thenReturn(List.of());
    }

    private CustomerInvoice invoice(String number, LocalDate date, BigDecimal total, CustomerInvoiceStatus status) {
        return CustomerInvoice.builder().id((long) number.hashCode()).invoiceNumber(number).customer(customer)
                .invoiceDate(date).dueDate(date.plusDays(30)).subtotalAmount(total).taxAmount(BigDecimal.ZERO)
                .totalAmount(total).amountPaid(BigDecimal.ZERO).status(status).build();
    }

    @Test
    void throwsWhenCustomerNotFound() {
        when(customersRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class,
                () -> service.generate(99L, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)));
    }

    @Test
    void invoicesBeforeTheRangeContributeOnlyToTheOpeningBalance() {
        CustomerInvoice priorInvoice = invoice("CINV-1", LocalDate.of(2026, 7, 15), new BigDecimal("100.00"), CustomerInvoiceStatus.POSTED);
        when(customerInvoiceRepository.findByCustomer(customer)).thenReturn(List.of(priorInvoice));

        CustomerStatementReport report = service.generate(1L, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        assertEquals(0, new BigDecimal("100.00").compareTo(report.getOpeningBalance()));
        assertTrue(report.getLines().isEmpty());
        assertEquals(0, new BigDecimal("100.00").compareTo(report.getClosingBalance()));
    }

    @Test
    void draftInvoicesAreExcludedEntirely() {
        CustomerInvoice draft = invoice("CINV-2", LocalDate.of(2026, 8, 10), new BigDecimal("50.00"), CustomerInvoiceStatus.DRAFT);
        when(customerInvoiceRepository.findByCustomer(customer)).thenReturn(List.of(draft));

        CustomerStatementReport report = service.generate(1L, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        assertTrue(report.getLines().isEmpty());
        assertEquals(0, BigDecimal.ZERO.compareTo(report.getClosingBalance()));
    }

    @Test
    void invoiceReceiptAndCreditNoteWithinRangeProduceARunningBalance() {
        CustomerInvoice inv = invoice("CINV-3", LocalDate.of(2026, 8, 5), new BigDecimal("200.00"), CustomerInvoiceStatus.PARTIALLY_PAID);
        when(customerInvoiceRepository.findByCustomer(customer)).thenReturn(List.of(inv));

        CustomerReceipt receipt = CustomerReceipt.builder().id(1L).customer(customer).invoice(inv)
                .amount(new BigDecimal("50.00")).receiptDate(LocalDate.of(2026, 8, 10)).build();
        when(customerReceiptRepository.findByCustomer(customer)).thenReturn(List.of(receipt));

        CustomerCreditNote creditNote = CustomerCreditNote.builder().id(1L).creditNoteNumber("CN-1")
                .customer(customer).invoice(inv).amount(new BigDecimal("20.00")).reason("Return")
                .issueDate(LocalDate.of(2026, 8, 15)).status(CreditNoteStatus.POSTED).build();
        when(customerCreditNoteRepository.findByCustomer(customer)).thenReturn(List.of(creditNote));

        CustomerStatementReport report = service.generate(1L, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        assertEquals(0, BigDecimal.ZERO.compareTo(report.getOpeningBalance()));
        assertEquals(3, report.getLines().size());
        // 200 (invoice) - 50 (receipt) - 20 (credit note) = 130
        assertEquals(0, new BigDecimal("130.00").compareTo(report.getClosingBalance()));
        assertEquals(0, new BigDecimal("200.00").compareTo(report.getLines().get(0).getRunningBalance()));
        assertEquals(0, new BigDecimal("150.00").compareTo(report.getLines().get(1).getRunningBalance()));
        assertEquals(0, new BigDecimal("130.00").compareTo(report.getLines().get(2).getRunningBalance()));
    }

    @Test
    void draftCreditNotesAreExcludedEntirely() {
        CustomerInvoice inv = invoice("CINV-4", LocalDate.of(2026, 8, 5), new BigDecimal("200.00"), CustomerInvoiceStatus.POSTED);
        when(customerInvoiceRepository.findByCustomer(customer)).thenReturn(List.of(inv));
        CustomerCreditNote draftCreditNote = CustomerCreditNote.builder().id(2L).creditNoteNumber("CN-2")
                .customer(customer).invoice(inv).amount(new BigDecimal("20.00")).reason("Pending")
                .issueDate(LocalDate.of(2026, 8, 15)).status(CreditNoteStatus.DRAFT).build();
        when(customerCreditNoteRepository.findByCustomer(customer)).thenReturn(List.of(draftCreditNote));

        CustomerStatementReport report = service.generate(1L, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        assertEquals(1, report.getLines().size());
        assertEquals("INVOICE", report.getLines().get(0).getType());
    }

    @Test
    void transactionsAfterTheRangeAreExcluded() {
        CustomerInvoice futureInvoice = invoice("CINV-5", LocalDate.of(2026, 9, 5), new BigDecimal("75.00"), CustomerInvoiceStatus.POSTED);
        when(customerInvoiceRepository.findByCustomer(customer)).thenReturn(List.of(futureInvoice));

        CustomerStatementReport report = service.generate(1L, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        assertTrue(report.getLines().isEmpty());
        assertEquals(0, BigDecimal.ZERO.compareTo(report.getOpeningBalance()));
        assertEquals(0, BigDecimal.ZERO.compareTo(report.getClosingBalance()));
    }
}
