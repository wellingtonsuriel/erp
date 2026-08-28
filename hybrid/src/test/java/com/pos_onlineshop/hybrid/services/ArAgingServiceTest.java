package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.customerInvoice.CustomerInvoice;
import com.pos_onlineshop.hybrid.customerInvoice.CustomerInvoiceRepository;
import com.pos_onlineshop.hybrid.customers.Customers;
import com.pos_onlineshop.hybrid.dtos.ArAgingReport;
import com.pos_onlineshop.hybrid.enums.CustomerInvoiceStatus;
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
class ArAgingServiceTest {

    @Mock private CustomerInvoiceRepository customerInvoiceRepository;
    private ArAgingService service;

    private final LocalDate asOfDate = LocalDate.of(2026, 8, 28);
    private final Customers customer = Customers.builder().id(1L).name("Wholesale Co").build();

    @BeforeEach
    void setUp() {
        service = new ArAgingService(customerInvoiceRepository);
    }

    private CustomerInvoice invoice(String number, LocalDate dueDate, String total, String paid) {
        return CustomerInvoice.builder()
                .id((long) number.hashCode()).invoiceNumber(number).customer(customer)
                .invoiceDate(dueDate.minusDays(30)).dueDate(dueDate)
                .subtotalAmount(new BigDecimal(total)).taxAmount(BigDecimal.ZERO)
                .totalAmount(new BigDecimal(total)).amountPaid(new BigDecimal(paid))
                .status(CustomerInvoiceStatus.PARTIALLY_PAID)
                .build();
    }

    @Test
    void bucketsInvoicesByDaysOverdueFromDueDate() {
        CustomerInvoice current = invoice("CINV-CURRENT", asOfDate.plusDays(5), "100.00", "0.00");
        CustomerInvoice bucket130 = invoice("CINV-1-30", asOfDate.minusDays(10), "200.00", "0.00");
        CustomerInvoice bucket3160 = invoice("CINV-31-60", asOfDate.minusDays(45), "300.00", "0.00");
        CustomerInvoice bucket6190 = invoice("CINV-61-90", asOfDate.minusDays(75), "400.00", "0.00");
        CustomerInvoice bucket90plus = invoice("CINV-90PLUS", asOfDate.minusDays(200), "500.00", "0.00");

        when(customerInvoiceRepository.findByStatusIn(List.of(CustomerInvoiceStatus.POSTED, CustomerInvoiceStatus.PARTIALLY_PAID)))
                .thenReturn(List.of(current, bucket130, bucket3160, bucket6190, bucket90plus));

        ArAgingReport report = service.generate(asOfDate);

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
        CustomerInvoice fullyPaidButStale = invoice("CINV-PAID", asOfDate.minusDays(5), "100.00", "100.00");
        when(customerInvoiceRepository.findByStatusIn(List.of(CustomerInvoiceStatus.POSTED, CustomerInvoiceStatus.PARTIALLY_PAID)))
                .thenReturn(List.of(fullyPaidButStale));

        ArAgingReport report = service.generate(asOfDate);

        assertTrue(report.getLines().isEmpty());
        assertEquals(0, BigDecimal.ZERO.compareTo(report.getTotalOutstanding()));
    }
}
