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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Customer statement: opening balance, every real transaction (invoice, receipt, credit
 * note) within [fromDate, toDate], and a running balance - see CustomerStatementReport's
 * class comment for the debit/credit sign convention. "Real" excludes DRAFT invoices/credit
 * notes (never posted, never affected the balance) and VOID ones (reversed, so they net to
 * nothing regardless).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerStatementService {

    private static final Set<CustomerInvoiceStatus> REAL_INVOICE_STATUSES =
            EnumSet.of(CustomerInvoiceStatus.POSTED, CustomerInvoiceStatus.PARTIALLY_PAID, CustomerInvoiceStatus.PAID);

    private final CustomersRepository customersRepository;
    private final CustomerInvoiceRepository customerInvoiceRepository;
    private final CustomerReceiptRepository customerReceiptRepository;
    private final CustomerCreditNoteRepository customerCreditNoteRepository;

    public CustomerStatementReport generate(Long customerId, LocalDate fromDate, LocalDate toDate) {
        Customers customer = customersRepository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + customerId));

        List<Movement> movements = new ArrayList<>();
        for (CustomerInvoice invoice : customerInvoiceRepository.findByCustomer(customer)) {
            if (REAL_INVOICE_STATUSES.contains(invoice.getStatus())) {
                movements.add(new Movement(invoice.getInvoiceDate(), "INVOICE", invoice.getInvoiceNumber(),
                        "Invoice " + invoice.getInvoiceNumber(), invoice.getTotalAmount(), BigDecimal.ZERO));
            }
        }
        for (CustomerReceipt receipt : customerReceiptRepository.findByCustomer(customer)) {
            movements.add(new Movement(receipt.getReceiptDate(), "RECEIPT",
                    receipt.getReference() != null ? receipt.getReference() : "Receipt " + receipt.getId(),
                    "Receipt against invoice " + receipt.getInvoice().getInvoiceNumber(), BigDecimal.ZERO, receipt.getAmount()));
        }
        for (CustomerCreditNote creditNote : customerCreditNoteRepository.findByCustomer(customer)) {
            if (creditNote.getStatus() == CreditNoteStatus.POSTED) {
                movements.add(new Movement(creditNote.getIssueDate(), "CREDIT_NOTE", creditNote.getCreditNoteNumber(),
                        "Credit note " + creditNote.getCreditNoteNumber() + " (" + creditNote.getReason() + ")",
                        BigDecimal.ZERO, creditNote.getAmount()));
            }
        }
        movements.sort(Comparator.comparing((Movement m) -> m.date()).thenComparing(m -> m.type()).thenComparing(m -> m.reference()));

        BigDecimal openingBalance = movements.stream()
                .filter(m -> m.date().isBefore(fromDate))
                .map(m -> m.debit().subtract(m.credit()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal running = openingBalance;
        List<CustomerStatementReport.Line> lines = new ArrayList<>();
        for (Movement m : movements) {
            if (m.date().isBefore(fromDate) || m.date().isAfter(toDate)) {
                continue;
            }
            running = running.add(m.debit()).subtract(m.credit());
            lines.add(CustomerStatementReport.Line.builder()
                    .date(m.date()).type(m.type()).reference(m.reference()).description(m.description())
                    .debit(m.debit()).credit(m.credit()).runningBalance(running)
                    .build());
        }

        return CustomerStatementReport.builder()
                .customerId(customer.getId())
                .customerName(customer.getName())
                .fromDate(fromDate)
                .toDate(toDate)
                .openingBalance(openingBalance)
                .lines(lines)
                .closingBalance(running)
                .build();
    }

    private record Movement(LocalDate date, String type, String reference, String description,
                             BigDecimal debit, BigDecimal credit) {
    }
}
