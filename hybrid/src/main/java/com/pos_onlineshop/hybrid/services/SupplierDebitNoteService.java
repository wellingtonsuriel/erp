package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.account.Account;
import com.pos_onlineshop.hybrid.account.AccountRepository;
import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.currency.CurrencyRepository;
import com.pos_onlineshop.hybrid.dtos.CreateSupplierDebitNoteRequest;
import com.pos_onlineshop.hybrid.dtos.SupplierDebitNoteResponse;
import com.pos_onlineshop.hybrid.enums.GLSourceModule;
import com.pos_onlineshop.hybrid.gl.ManualLineSpec;
import com.pos_onlineshop.hybrid.journalEntry.JournalEntry;
import com.pos_onlineshop.hybrid.supplierDebitNote.SupplierDebitNote;
import com.pos_onlineshop.hybrid.supplierDebitNote.SupplierDebitNoteRepository;
import com.pos_onlineshop.hybrid.supplierInvoice.SupplierInvoice;
import com.pos_onlineshop.hybrid.supplierInvoice.SupplierInvoiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Debit notes against a SupplierInvoice - see SupplierDebitNote's class comment for the
 * lifecycle and for why the GL credit account is chosen per invocation (1200 Inventory for a
 * PO-linked invoice, 5300 Operating Expenses for a standalone one) via postManual, rather
 * than a static PostingRule.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class SupplierDebitNoteService {

    private static final String INVENTORY_ACCOUNT_CODE = "1200";
    private static final String OPERATING_EXPENSES_ACCOUNT_CODE = "5300";
    private static final String ACCOUNTS_PAYABLE_ACCOUNT_CODE = "2100";

    private final SupplierDebitNoteRepository supplierDebitNoteRepository;
    private final SupplierInvoiceRepository supplierInvoiceRepository;
    private final CurrencyRepository currencyRepository;
    private final AccountRepository accountRepository;
    private final GLPostingService glPostingService;
    private final CurrencyService currencyService;

    public List<SupplierDebitNoteResponse> findAll() {
        return supplierDebitNoteRepository.findAllByOrderByIdDesc().stream()
                .map(this::toResponse).collect(Collectors.toList());
    }

    public SupplierDebitNoteResponse findById(Long id) {
        return toResponse(findOrThrow(id));
    }

    public SupplierDebitNoteResponse createDebitNote(CreateSupplierDebitNoteRequest request) {
        if (supplierDebitNoteRepository.existsByDebitNoteNumber(request.getDebitNoteNumber())) {
            throw new IllegalArgumentException(
                    "A debit note with number " + request.getDebitNoteNumber() + " already exists");
        }
        SupplierInvoice invoice = supplierInvoiceRepository.findById(request.getInvoiceId())
                .orElseThrow(() -> new IllegalArgumentException("Invoice not found: " + request.getInvoiceId()));
        Currency currency = currencyRepository.findById(request.getCurrencyId())
                .orElseThrow(() -> new IllegalArgumentException("Currency not found: " + request.getCurrencyId()));
        if (request.getAmount().compareTo(invoice.getOutstandingAmount()) > 0) {
            throw new IllegalArgumentException(
                    "Debit note amount " + request.getAmount() + " exceeds invoice " + invoice.getInvoiceNumber()
                            + "'s outstanding balance of " + invoice.getOutstandingAmount());
        }

        SupplierDebitNote debitNote = SupplierDebitNote.builder()
                .debitNoteNumber(request.getDebitNoteNumber())
                .supplier(invoice.getSupplier())
                .invoice(invoice)
                .currency(currency)
                .amount(request.getAmount())
                .reason(request.getReason())
                .issueDate(request.getIssueDate())
                .build();

        SupplierDebitNote saved = supplierDebitNoteRepository.save(debitNote);
        log.info("Created supplier debit note {} against invoice {}",
                saved.getDebitNoteNumber(), invoice.getInvoiceNumber());
        return toResponse(saved);
    }

    public SupplierDebitNoteResponse postDebitNote(Long id) {
        SupplierDebitNote debitNote = findOrThrow(id);
        if (!debitNote.canBePosted()) {
            throw new IllegalStateException(
                    "Debit note " + debitNote.getDebitNoteNumber() + " cannot be posted from status " + debitNote.getStatus());
        }

        SupplierInvoice invoice = debitNote.getInvoice();
        // Re-check against the invoice's CURRENT outstanding balance, not the balance at
        // creation time - a payment or another debit note may have been applied since.
        invoice.applyPayment(debitNote.getAmount());
        supplierInvoiceRepository.save(invoice);

        JournalEntry entry = postToGeneralLedger(debitNote, invoice);
        debitNote.post(entry);
        SupplierDebitNote saved = supplierDebitNoteRepository.save(debitNote);

        log.info("Posted supplier debit note {} (GL entry #{}) against invoice {} - now {}",
                saved.getDebitNoteNumber(), entry.getEntryNumber(), invoice.getInvoiceNumber(), invoice.getStatus());
        return toResponse(saved);
    }

    public SupplierDebitNoteResponse voidDebitNote(Long id, String reason) {
        SupplierDebitNote debitNote = findOrThrow(id);
        debitNote.voidDebitNote(reason);
        return toResponse(supplierDebitNoteRepository.save(debitNote));
    }

    private JournalEntry postToGeneralLedger(SupplierDebitNote debitNote, SupplierInvoice invoice) {
        Currency currency = debitNote.getCurrency();
        Currency baseCurrency = currencyService.getBaseCurrency();
        BigDecimal exchangeRate = BigDecimal.ONE;
        if (currency != null && !currency.equals(baseCurrency)) {
            exchangeRate = currencyService.getExchangeRate(currency, baseCurrency);
        }

        Account accountsPayable = accountRepository.findByCode(ACCOUNTS_PAYABLE_ACCOUNT_CODE)
                .orElseThrow(() -> new IllegalStateException("Chart of accounts is missing " + ACCOUNTS_PAYABLE_ACCOUNT_CODE));
        String creditAccountCode = invoice.isPoLinked() ? INVENTORY_ACCOUNT_CODE : OPERATING_EXPENSES_ACCOUNT_CODE;
        Account creditAccount = accountRepository.findByCode(creditAccountCode)
                .orElseThrow(() -> new IllegalStateException("Chart of accounts is missing " + creditAccountCode));

        String memo = "Debit note " + debitNote.getDebitNoteNumber() + " against invoice "
                + invoice.getInvoiceNumber() + " (" + invoice.getSupplier().getName() + ")";
        List<ManualLineSpec> specs = List.of(
                new ManualLineSpec(accountsPayable, debitNote.getAmount(), BigDecimal.ZERO, currency, exchangeRate, invoice.getShop(), memo),
                new ManualLineSpec(creditAccount, BigDecimal.ZERO, debitNote.getAmount(), currency, exchangeRate, invoice.getShop(), memo));

        return glPostingService.postManual(
                "SUPPLIER-DEBIT-NOTE-" + debitNote.getId(),
                debitNote.getIssueDate(),
                memo,
                GLSourceModule.INVENTORY,
                "SUPPLIER_DEBIT_NOTE",
                debitNote.getId(),
                specs,
                "system");
    }

    private SupplierDebitNote findOrThrow(Long id) {
        return supplierDebitNoteRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Debit note not found: " + id));
    }

    private SupplierDebitNoteResponse toResponse(SupplierDebitNote debitNote) {
        return SupplierDebitNoteResponse.builder()
                .id(debitNote.getId())
                .debitNoteNumber(debitNote.getDebitNoteNumber())
                .supplierId(debitNote.getSupplier().getId())
                .supplierName(debitNote.getSupplier().getName())
                .invoiceId(debitNote.getInvoice().getId())
                .invoiceNumber(debitNote.getInvoice().getInvoiceNumber())
                .currencyCode(debitNote.getCurrency() != null ? debitNote.getCurrency().getCode() : null)
                .amount(debitNote.getAmount())
                .reason(debitNote.getReason())
                .issueDate(debitNote.getIssueDate())
                .status(debitNote.getStatus().name())
                .voidedReason(debitNote.getVoidedReason())
                .createdAt(debitNote.getCreatedAt())
                .postedJournalEntryId(debitNote.getPostedJournalEntry() != null ? debitNote.getPostedJournalEntry().getId() : null)
                .postedJournalEntryNumber(debitNote.getPostedJournalEntry() != null ? debitNote.getPostedJournalEntry().getEntryNumber() : null)
                .build();
    }
}
