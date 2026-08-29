package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.currency.CurrencyRepository;
import com.pos_onlineshop.hybrid.customerCreditNote.CustomerCreditNote;
import com.pos_onlineshop.hybrid.customerCreditNote.CustomerCreditNoteRepository;
import com.pos_onlineshop.hybrid.customerInvoice.CustomerInvoice;
import com.pos_onlineshop.hybrid.customerInvoice.CustomerInvoiceRepository;
import com.pos_onlineshop.hybrid.customers.Customers;
import com.pos_onlineshop.hybrid.dtos.CreateCustomerCreditNoteRequest;
import com.pos_onlineshop.hybrid.dtos.CustomerCreditNoteResponse;
import com.pos_onlineshop.hybrid.enums.CreditNoteStatus;
import com.pos_onlineshop.hybrid.enums.CustomerInvoiceStatus;
import com.pos_onlineshop.hybrid.enums.FinancialEventType;
import com.pos_onlineshop.hybrid.gl.FinancialEvent;
import com.pos_onlineshop.hybrid.journalEntry.JournalEntry;
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
class CustomerCreditNoteServiceTest {

    @Mock private CustomerCreditNoteRepository customerCreditNoteRepository;
    @Mock private CustomerInvoiceRepository customerInvoiceRepository;
    @Mock private CurrencyRepository currencyRepository;
    @Mock private GLPostingService glPostingService;
    @Mock private CurrencyService currencyService;

    private CustomerCreditNoteService service;

    private Currency currency;
    private CustomerInvoice postedInvoice;

    @BeforeEach
    void setUp() {
        service = new CustomerCreditNoteService(customerCreditNoteRepository, customerInvoiceRepository,
                currencyRepository, glPostingService, currencyService);

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
    }

    private CreateCustomerCreditNoteRequest request(BigDecimal amount) {
        CreateCustomerCreditNoteRequest request = new CreateCustomerCreditNoteRequest();
        request.setCreditNoteNumber("CN-1");
        request.setInvoiceId(10L);
        request.setCurrencyId(1L);
        request.setAmount(amount);
        request.setReason("Goods returned");
        request.setIssueDate(LocalDate.now());
        return request;
    }

    @Test
    void createCreditNoteRejectsADuplicateNumber() {
        when(customerCreditNoteRepository.existsByCreditNoteNumber("CN-1")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> service.createCreditNote(request(new BigDecimal("40.00"))));
        verifyNoInteractions(customerInvoiceRepository);
    }

    @Test
    void createCreditNoteRejectsAnAmountExceedingTheOutstandingBalance() {
        when(customerCreditNoteRepository.existsByCreditNoteNumber("CN-1")).thenReturn(false);
        when(customerInvoiceRepository.findById(10L)).thenReturn(Optional.of(postedInvoice));

        assertThrows(IllegalArgumentException.class, () -> service.createCreditNote(request(new BigDecimal("150.00"))));
        verify(customerCreditNoteRepository, never()).save(any());
    }

    @Test
    void createCreditNoteSavesADraft() {
        when(customerCreditNoteRepository.existsByCreditNoteNumber("CN-1")).thenReturn(false);
        when(customerInvoiceRepository.findById(10L)).thenReturn(Optional.of(postedInvoice));
        when(customerCreditNoteRepository.save(any(CustomerCreditNote.class))).thenAnswer(inv -> {
            CustomerCreditNote cn = inv.getArgument(0);
            cn.setId(50L);
            return cn;
        });

        CustomerCreditNoteResponse response = service.createCreditNote(request(new BigDecimal("40.00")));

        assertEquals("DRAFT", response.getStatus());
        assertEquals("CINV-10", response.getInvoiceNumber());
        verifyNoInteractions(glPostingService);
    }

    private CustomerCreditNote draftCreditNote(BigDecimal amount) {
        return CustomerCreditNote.builder().id(50L).creditNoteNumber("CN-1")
                .customer(postedInvoice.getCustomer()).invoice(postedInvoice).currency(currency)
                .amount(amount).reason("Goods returned").issueDate(LocalDate.now())
                .status(CreditNoteStatus.DRAFT).build();
    }

    @Test
    void postCreditNoteAppliesToTheInvoiceAndPostsToTheGl() {
        CustomerCreditNote creditNote = draftCreditNote(new BigDecimal("40.00"));
        when(customerCreditNoteRepository.findById(50L)).thenReturn(Optional.of(creditNote));
        when(customerInvoiceRepository.save(any(CustomerInvoice.class))).thenAnswer(inv -> inv.getArgument(0));
        JournalEntry entry = JournalEntry.builder().id(200L).entryNumber(70L).build();
        when(glPostingService.post(any(FinancialEvent.class))).thenReturn(entry);
        when(customerCreditNoteRepository.save(any(CustomerCreditNote.class))).thenAnswer(inv -> inv.getArgument(0));

        CustomerCreditNoteResponse response = service.postCreditNote(50L);

        assertEquals("POSTED", response.getStatus());
        assertEquals(0, new BigDecimal("40.00").compareTo(postedInvoice.getAmountPaid()));
        assertEquals(CustomerInvoiceStatus.PARTIALLY_PAID, postedInvoice.getStatus());
        assertEquals(70L, response.getPostedJournalEntryNumber());

        ArgumentCaptor<FinancialEvent> captor = ArgumentCaptor.forClass(FinancialEvent.class);
        verify(glPostingService).post(captor.capture());
        assertEquals(FinancialEventType.CUSTOMER_CREDIT_NOTE, captor.getValue().getEventType());
        assertEquals("CUSTOMER-CREDIT-NOTE-50", captor.getValue().getIdempotencyKey());
        assertEquals(0, new BigDecimal("40.00").compareTo(captor.getValue().getGrossAmount()));
    }

    @Test
    void postCreditNoteRejectsAnAlreadyPostedCreditNote() {
        CustomerCreditNote creditNote = draftCreditNote(new BigDecimal("40.00"));
        creditNote.setStatus(CreditNoteStatus.POSTED);
        when(customerCreditNoteRepository.findById(50L)).thenReturn(Optional.of(creditNote));

        assertThrows(IllegalStateException.class, () -> service.postCreditNote(50L));
        verifyNoInteractions(glPostingService);
    }

    @Test
    void postCreditNoteRejectsWhenTheInvoicesCurrentOutstandingBalanceIsTooLow() {
        // amount was valid at creation time, but the invoice has since been paid down further
        CustomerCreditNote creditNote = draftCreditNote(new BigDecimal("90.00"));
        postedInvoice.applyPayment(new BigDecimal("50.00")); // only 50.00 outstanding now
        when(customerCreditNoteRepository.findById(50L)).thenReturn(Optional.of(creditNote));

        assertThrows(IllegalArgumentException.class, () -> service.postCreditNote(50L));
        verifyNoInteractions(glPostingService);
    }

    @Test
    void voidCreditNoteRejectsAPostedCreditNote() {
        CustomerCreditNote creditNote = draftCreditNote(new BigDecimal("40.00"));
        creditNote.setStatus(CreditNoteStatus.POSTED);
        when(customerCreditNoteRepository.findById(50L)).thenReturn(Optional.of(creditNote));

        assertThrows(IllegalStateException.class, () -> service.voidCreditNote(50L, "changed mind"));
    }

    @Test
    void voidCreditNoteVoidsADraft() {
        CustomerCreditNote creditNote = draftCreditNote(new BigDecimal("40.00"));
        when(customerCreditNoteRepository.findById(50L)).thenReturn(Optional.of(creditNote));
        when(customerCreditNoteRepository.save(any(CustomerCreditNote.class))).thenAnswer(inv -> inv.getArgument(0));

        CustomerCreditNoteResponse response = service.voidCreditNote(50L, "changed mind");

        assertEquals("VOID", response.getStatus());
        assertEquals("changed mind", response.getVoidedReason());
    }
}
