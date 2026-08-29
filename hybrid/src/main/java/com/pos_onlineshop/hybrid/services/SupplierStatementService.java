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

/** Mirrors CustomerStatementService exactly - see its class comment and
 * SupplierStatementReport's class comment for the debit/credit sign convention. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SupplierStatementService {

    private static final Set<SupplierInvoiceStatus> REAL_INVOICE_STATUSES =
            EnumSet.of(SupplierInvoiceStatus.POSTED, SupplierInvoiceStatus.PARTIALLY_PAID, SupplierInvoiceStatus.PAID);

    private final SuppliersRepository suppliersRepository;
    private final SupplierInvoiceRepository supplierInvoiceRepository;
    private final SupplierPaymentRepository supplierPaymentRepository;
    private final SupplierDebitNoteRepository supplierDebitNoteRepository;

    public SupplierStatementReport generate(Long supplierId, LocalDate fromDate, LocalDate toDate) {
        Suppliers supplier = suppliersRepository.findById(supplierId)
                .orElseThrow(() -> new IllegalArgumentException("Supplier not found: " + supplierId));

        List<Movement> movements = new ArrayList<>();
        for (SupplierInvoice invoice : supplierInvoiceRepository.findBySupplier(supplier)) {
            if (REAL_INVOICE_STATUSES.contains(invoice.getStatus())) {
                movements.add(new Movement(invoice.getInvoiceDate(), "INVOICE", invoice.getInvoiceNumber(),
                        "Invoice " + invoice.getInvoiceNumber(), invoice.getTotalAmount(), BigDecimal.ZERO));
            }
        }
        for (SupplierPayment payment : supplierPaymentRepository.findBySupplier(supplier)) {
            movements.add(new Movement(payment.getPaymentDate(), "PAYMENT",
                    payment.getReference() != null ? payment.getReference() : "Payment " + payment.getId(),
                    "Payment against invoice " + payment.getInvoice().getInvoiceNumber(), BigDecimal.ZERO, payment.getAmount()));
        }
        for (SupplierDebitNote debitNote : supplierDebitNoteRepository.findBySupplier(supplier)) {
            if (debitNote.getStatus() == DebitNoteStatus.POSTED) {
                movements.add(new Movement(debitNote.getIssueDate(), "DEBIT_NOTE", debitNote.getDebitNoteNumber(),
                        "Debit note " + debitNote.getDebitNoteNumber() + " (" + debitNote.getReason() + ")",
                        BigDecimal.ZERO, debitNote.getAmount()));
            }
        }
        movements.sort(Comparator.comparing((Movement m) -> m.date()).thenComparing(m -> m.type()).thenComparing(m -> m.reference()));

        BigDecimal openingBalance = movements.stream()
                .filter(m -> m.date().isBefore(fromDate))
                .map(m -> m.debit().subtract(m.credit()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal running = openingBalance;
        List<SupplierStatementReport.Line> lines = new ArrayList<>();
        for (Movement m : movements) {
            if (m.date().isBefore(fromDate) || m.date().isAfter(toDate)) {
                continue;
            }
            running = running.add(m.debit()).subtract(m.credit());
            lines.add(SupplierStatementReport.Line.builder()
                    .date(m.date()).type(m.type()).reference(m.reference()).description(m.description())
                    .debit(m.debit()).credit(m.credit()).runningBalance(running)
                    .build());
        }

        return SupplierStatementReport.builder()
                .supplierId(supplier.getId())
                .supplierName(supplier.getName())
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
