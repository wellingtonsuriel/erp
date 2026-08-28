package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.dtos.ApAgingReport;
import com.pos_onlineshop.hybrid.enums.SupplierInvoiceStatus;
import com.pos_onlineshop.hybrid.suppliers.Suppliers;
import com.pos_onlineshop.hybrid.supplierInvoice.SupplierInvoice;
import com.pos_onlineshop.hybrid.supplierInvoice.SupplierInvoiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApAgingServiceTest {

    @Mock private SupplierInvoiceRepository supplierInvoiceRepository;
    private ApAgingService service;

    private final LocalDate asOfDate = LocalDate.of(2026, 8, 28);
    private final Suppliers supplier = Suppliers.builder().id(1L).name("Acme Supplies").build();

    @BeforeEach
    void setUp() {
        service = new ApAgingService(supplierInvoiceRepository);
    }

    private SupplierInvoice invoice(String number, LocalDate dueDate, String total, String paid) {
        return SupplierInvoice.builder()
                .id((long) number.hashCode()).invoiceNumber(number).supplier(supplier)
                .invoiceDate(dueDate.minusDays(30)).dueDate(dueDate)
                .subtotalAmount(new BigDecimal(total)).taxAmount(BigDecimal.ZERO)
                .totalAmount(new BigDecimal(total)).amountPaid(new BigDecimal(paid))
                .status(SupplierInvoiceStatus.PARTIALLY_PAID)
                .build();
    }

    @Test
    void bucketsInvoicesByDaysOverdueFromDueDate() {
        SupplierInvoice current = invoice("INV-CURRENT", asOfDate.plusDays(5), "100.00", "0.00");
        SupplierInvoice bucket130 = invoice("INV-1-30", asOfDate.minusDays(10), "200.00", "0.00");
        SupplierInvoice bucket3160 = invoice("INV-31-60", asOfDate.minusDays(45), "300.00", "0.00");
        SupplierInvoice bucket6190 = invoice("INV-61-90", asOfDate.minusDays(75), "400.00", "0.00");
        SupplierInvoice bucket90plus = invoice("INV-90PLUS", asOfDate.minusDays(200), "500.00", "0.00");

        when(supplierInvoiceRepository.findByStatusIn(List.of(SupplierInvoiceStatus.POSTED, SupplierInvoiceStatus.PARTIALLY_PAID)))
                .thenReturn(List.of(current, bucket130, bucket3160, bucket6190, bucket90plus));

        ApAgingReport report = service.generate(asOfDate);

        assertEquals(5, report.getLines().size());
        assertEquals(0, new BigDecimal("100.00").compareTo(report.getBucketTotals().get("Current")));
        assertEquals(0, new BigDecimal("200.00").compareTo(report.getBucketTotals().get("1-30")));
        assertEquals(0, new BigDecimal("300.00").compareTo(report.getBucketTotals().get("31-60")));
        assertEquals(0, new BigDecimal("400.00").compareTo(report.getBucketTotals().get("61-90")));
        assertEquals(0, new BigDecimal("500.00").compareTo(report.getBucketTotals().get("90+")));
        assertEquals(0, new BigDecimal("1500.00").compareTo(report.getTotalOutstanding()));
    }

    @Test
    void fullyPaidInvoicesAreExcludedEvenIfStatusStillReturnedByRepository() {
        SupplierInvoice fullyPaidButStale = invoice("INV-PAID", asOfDate.minusDays(5), "100.00", "100.00");
        when(supplierInvoiceRepository.findByStatusIn(List.of(SupplierInvoiceStatus.POSTED, SupplierInvoiceStatus.PARTIALLY_PAID)))
                .thenReturn(List.of(fullyPaidButStale));

        ApAgingReport report = service.generate(asOfDate);

        assertTrue(report.getLines().isEmpty());
        assertEquals(0, BigDecimal.ZERO.compareTo(report.getTotalOutstanding()));
    }
}
