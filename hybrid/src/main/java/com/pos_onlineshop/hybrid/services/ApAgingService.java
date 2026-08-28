package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.dtos.ApAgingReport;
import com.pos_onlineshop.hybrid.supplierInvoice.SupplierInvoice;
import com.pos_onlineshop.hybrid.supplierInvoice.SupplierInvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.pos_onlineshop.hybrid.enums.SupplierInvoiceStatus.PARTIALLY_PAID;
import static com.pos_onlineshop.hybrid.enums.SupplierInvoiceStatus.POSTED;

/**
 * Bucketed by days-overdue from due date, not invoice date. Reads directly from the
 * SupplierInvoice subledger (the source of AP open-item detail), not from the GL - the GL
 * account 2100 is the control-account total this report's sum should reconcile to, which is
 * exactly what a future control-account reconciliation service would check (not implemented
 * this pass).
 */
@Service
@RequiredArgsConstructor
public class ApAgingService {

    private static final List<String> BUCKET_ORDER = List.of("Current", "1-30", "31-60", "61-90", "90+");

    private final SupplierInvoiceRepository supplierInvoiceRepository;

    @Transactional(readOnly = true)
    public ApAgingReport generate(LocalDate asOfDate) {
        List<SupplierInvoice> outstanding = supplierInvoiceRepository.findByStatusIn(List.of(POSTED, PARTIALLY_PAID))
                .stream()
                .filter(inv -> inv.getOutstandingAmount().compareTo(BigDecimal.ZERO) > 0)
                .collect(Collectors.toList());

        Map<String, BigDecimal> bucketTotals = new LinkedHashMap<>();
        BUCKET_ORDER.forEach(b -> bucketTotals.put(b, BigDecimal.ZERO));

        List<ApAgingReport.Line> lines = outstanding.stream().map(invoice -> {
            long daysOverdue = java.time.temporal.ChronoUnit.DAYS.between(invoice.getDueDate(), asOfDate);
            String bucket = bucketFor(daysOverdue);
            bucketTotals.merge(bucket, invoice.getOutstandingAmount(), BigDecimal::add);

            return ApAgingReport.Line.builder()
                    .supplierId(invoice.getSupplier().getId())
                    .supplierName(invoice.getSupplier().getName())
                    .invoiceId(invoice.getId())
                    .invoiceNumber(invoice.getInvoiceNumber())
                    .invoiceDate(invoice.getInvoiceDate())
                    .dueDate(invoice.getDueDate())
                    .originalAmount(invoice.getTotalAmount())
                    .outstandingAmount(invoice.getOutstandingAmount())
                    .daysOverdue(Math.max(daysOverdue, 0))
                    .bucket(bucket)
                    .build();
        }).collect(Collectors.toList());

        BigDecimal totalOutstanding = lines.stream()
                .map(ApAgingReport.Line::getOutstandingAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return ApAgingReport.builder()
                .asOfDate(asOfDate)
                .lines(lines)
                .bucketTotals(bucketTotals)
                .totalOutstanding(totalOutstanding)
                .build();
    }

    private String bucketFor(long daysOverdue) {
        if (daysOverdue <= 0) return "Current";
        if (daysOverdue <= 30) return "1-30";
        if (daysOverdue <= 60) return "31-60";
        if (daysOverdue <= 90) return "61-90";
        return "90+";
    }
}
