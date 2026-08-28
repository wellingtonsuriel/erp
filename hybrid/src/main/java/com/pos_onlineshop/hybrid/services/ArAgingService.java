package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.customerInvoice.CustomerInvoice;
import com.pos_onlineshop.hybrid.customerInvoice.CustomerInvoiceRepository;
import com.pos_onlineshop.hybrid.dtos.ArAgingReport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.pos_onlineshop.hybrid.enums.CustomerInvoiceStatus.PARTIALLY_PAID;
import static com.pos_onlineshop.hybrid.enums.CustomerInvoiceStatus.POSTED;

/** Mirrors ApAgingService exactly - see its class comment for the design rationale. */
@Service
@RequiredArgsConstructor
public class ArAgingService {

    private static final List<String> BUCKET_ORDER = List.of("Current", "1-30", "31-60", "61-90", "90+");

    private final CustomerInvoiceRepository customerInvoiceRepository;

    @Transactional(readOnly = true)
    public ArAgingReport generate(LocalDate asOfDate) {
        List<CustomerInvoice> outstanding = customerInvoiceRepository.findByStatusIn(List.of(POSTED, PARTIALLY_PAID))
                .stream()
                .filter(inv -> inv.getOutstandingAmount().compareTo(BigDecimal.ZERO) > 0)
                .collect(Collectors.toList());

        Map<String, BigDecimal> bucketTotals = new LinkedHashMap<>();
        BUCKET_ORDER.forEach(b -> bucketTotals.put(b, BigDecimal.ZERO));

        List<ArAgingReport.Line> lines = outstanding.stream().map(invoice -> {
            long daysOverdue = java.time.temporal.ChronoUnit.DAYS.between(invoice.getDueDate(), asOfDate);
            String bucket = bucketFor(daysOverdue);
            bucketTotals.merge(bucket, invoice.getOutstandingAmount(), BigDecimal::add);

            return ArAgingReport.Line.builder()
                    .customerId(invoice.getCustomer().getId())
                    .customerName(invoice.getCustomer().getName())
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
                .map(ArAgingReport.Line::getOutstandingAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return ArAgingReport.builder()
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
