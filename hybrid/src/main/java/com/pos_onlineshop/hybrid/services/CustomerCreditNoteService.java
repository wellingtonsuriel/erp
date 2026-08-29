package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.currency.CurrencyRepository;
import com.pos_onlineshop.hybrid.customerCreditNote.CustomerCreditNote;
import com.pos_onlineshop.hybrid.customerCreditNote.CustomerCreditNoteRepository;
import com.pos_onlineshop.hybrid.customerInvoice.CustomerInvoice;
import com.pos_onlineshop.hybrid.customerInvoice.CustomerInvoiceRepository;
import com.pos_onlineshop.hybrid.dtos.CreateCustomerCreditNoteRequest;
import com.pos_onlineshop.hybrid.dtos.CustomerCreditNoteResponse;
import com.pos_onlineshop.hybrid.enums.FinancialEventType;
import com.pos_onlineshop.hybrid.enums.GLSourceModule;
import com.pos_onlineshop.hybrid.gl.FinancialEvent;
import com.pos_onlineshop.hybrid.journalEntry.JournalEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Credit notes against a CustomerInvoice - see CustomerCreditNote's class comment for the
 * DRAFT -> POSTED -> (VOID from DRAFT only) lifecycle. Posting does two things atomically:
 * reduces the invoice's outstanding balance via CustomerInvoice.applyPayment() (the same
 * guard a real receipt uses - a credit note is economically identical to a receipt from the
 * invoice's point of view, it just isn't cash) and posts CUSTOMER_CREDIT_NOTE to the GL
 * (DEBIT 4900 Sales Returns & Allowances, CREDIT 1100 Accounts Receivable).
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CustomerCreditNoteService {

    private final CustomerCreditNoteRepository customerCreditNoteRepository;
    private final CustomerInvoiceRepository customerInvoiceRepository;
    private final CurrencyRepository currencyRepository;
    private final GLPostingService glPostingService;
    private final CurrencyService currencyService;

    public List<CustomerCreditNoteResponse> findAll() {
        return customerCreditNoteRepository.findAllByOrderByIdDesc().stream()
                .map(this::toResponse).collect(Collectors.toList());
    }

    public CustomerCreditNoteResponse findById(Long id) {
        return toResponse(findOrThrow(id));
    }

    public CustomerCreditNoteResponse createCreditNote(CreateCustomerCreditNoteRequest request) {
        if (customerCreditNoteRepository.existsByCreditNoteNumber(request.getCreditNoteNumber())) {
            throw new IllegalArgumentException(
                    "A credit note with number " + request.getCreditNoteNumber() + " already exists");
        }
        CustomerInvoice invoice = customerInvoiceRepository.findById(request.getInvoiceId())
                .orElseThrow(() -> new IllegalArgumentException("Invoice not found: " + request.getInvoiceId()));
        Currency currency = currencyRepository.findById(request.getCurrencyId())
                .orElseThrow(() -> new IllegalArgumentException("Currency not found: " + request.getCurrencyId()));
        if (request.getAmount().compareTo(invoice.getOutstandingAmount()) > 0) {
            throw new IllegalArgumentException(
                    "Credit note amount " + request.getAmount() + " exceeds invoice " + invoice.getInvoiceNumber()
                            + "'s outstanding balance of " + invoice.getOutstandingAmount());
        }

        CustomerCreditNote creditNote = CustomerCreditNote.builder()
                .creditNoteNumber(request.getCreditNoteNumber())
                .customer(invoice.getCustomer())
                .invoice(invoice)
                .currency(currency)
                .amount(request.getAmount())
                .reason(request.getReason())
                .issueDate(request.getIssueDate())
                .build();

        CustomerCreditNote saved = customerCreditNoteRepository.save(creditNote);
        log.info("Created customer credit note {} against invoice {}",
                saved.getCreditNoteNumber(), invoice.getInvoiceNumber());
        return toResponse(saved);
    }

    public CustomerCreditNoteResponse postCreditNote(Long id) {
        CustomerCreditNote creditNote = findOrThrow(id);
        if (!creditNote.canBePosted()) {
            throw new IllegalStateException(
                    "Credit note " + creditNote.getCreditNoteNumber() + " cannot be posted from status " + creditNote.getStatus());
        }

        CustomerInvoice invoice = creditNote.getInvoice();
        // Re-check against the invoice's CURRENT outstanding balance, not the balance at
        // creation time - a receipt or another credit note may have been applied since.
        invoice.applyPayment(creditNote.getAmount());
        customerInvoiceRepository.save(invoice);

        JournalEntry entry = postToGeneralLedger(creditNote, invoice);
        creditNote.post(entry);
        CustomerCreditNote saved = customerCreditNoteRepository.save(creditNote);

        log.info("Posted customer credit note {} (GL entry #{}) against invoice {} - now {}",
                saved.getCreditNoteNumber(), entry.getEntryNumber(), invoice.getInvoiceNumber(), invoice.getStatus());
        return toResponse(saved);
    }

    public CustomerCreditNoteResponse voidCreditNote(Long id, String reason) {
        CustomerCreditNote creditNote = findOrThrow(id);
        creditNote.voidCreditNote(reason);
        return toResponse(customerCreditNoteRepository.save(creditNote));
    }

    private JournalEntry postToGeneralLedger(CustomerCreditNote creditNote, CustomerInvoice invoice) {
        Currency currency = creditNote.getCurrency();
        Currency baseCurrency = currencyService.getBaseCurrency();
        java.math.BigDecimal exchangeRate = java.math.BigDecimal.ONE;
        if (currency != null && !currency.equals(baseCurrency)) {
            exchangeRate = currencyService.getExchangeRate(currency, baseCurrency);
        }

        FinancialEvent event = FinancialEvent.builder()
                .eventType(FinancialEventType.CUSTOMER_CREDIT_NOTE)
                .sourceModule(GLSourceModule.ORDER)
                .sourceReferenceType("CUSTOMER_CREDIT_NOTE")
                .sourceReferenceId(creditNote.getId())
                .idempotencyKey("CUSTOMER-CREDIT-NOTE-" + creditNote.getId())
                .eventDate(creditNote.getIssueDate())
                .description("Credit note " + creditNote.getCreditNoteNumber() + " against invoice "
                        + invoice.getInvoiceNumber() + " (" + invoice.getCustomer().getName() + ")")
                .shop(invoice.getShop())
                .currency(currency)
                .exchangeRate(exchangeRate)
                .grossAmount(creditNote.getAmount())
                .postedBy("system")
                .build();

        return glPostingService.post(event);
    }

    private CustomerCreditNote findOrThrow(Long id) {
        return customerCreditNoteRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Credit note not found: " + id));
    }

    private CustomerCreditNoteResponse toResponse(CustomerCreditNote creditNote) {
        return CustomerCreditNoteResponse.builder()
                .id(creditNote.getId())
                .creditNoteNumber(creditNote.getCreditNoteNumber())
                .customerId(creditNote.getCustomer().getId())
                .customerName(creditNote.getCustomer().getName())
                .invoiceId(creditNote.getInvoice().getId())
                .invoiceNumber(creditNote.getInvoice().getInvoiceNumber())
                .currencyCode(creditNote.getCurrency() != null ? creditNote.getCurrency().getCode() : null)
                .amount(creditNote.getAmount())
                .reason(creditNote.getReason())
                .issueDate(creditNote.getIssueDate())
                .status(creditNote.getStatus().name())
                .voidedReason(creditNote.getVoidedReason())
                .createdAt(creditNote.getCreatedAt())
                .postedJournalEntryId(creditNote.getPostedJournalEntry() != null ? creditNote.getPostedJournalEntry().getId() : null)
                .postedJournalEntryNumber(creditNote.getPostedJournalEntry() != null ? creditNote.getPostedJournalEntry().getEntryNumber() : null)
                .build();
    }
}
