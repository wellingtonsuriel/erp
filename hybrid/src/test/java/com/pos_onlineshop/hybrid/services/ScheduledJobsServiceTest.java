package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.customerInvoice.CustomerInvoice;
import com.pos_onlineshop.hybrid.customerInvoice.CustomerInvoiceRepository;
import com.pos_onlineshop.hybrid.customers.Customers;
import com.pos_onlineshop.hybrid.enums.AuditAction;
import com.pos_onlineshop.hybrid.enums.CustomerInvoiceStatus;
import com.pos_onlineshop.hybrid.enums.SupplierInvoiceStatus;
import com.pos_onlineshop.hybrid.suppliers.Suppliers;
import com.pos_onlineshop.hybrid.supplierInvoice.SupplierInvoice;
import com.pos_onlineshop.hybrid.supplierInvoice.SupplierInvoiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScheduledJobsServiceTest {

    @Mock private OrderService orderService;
    @Mock private CustomerInvoiceRepository customerInvoiceRepository;
    @Mock private SupplierInvoiceRepository supplierInvoiceRepository;
    @Mock private AuditLogService auditLogService;

    private ScheduledJobsService service;

    @BeforeEach
    void setUp() {
        service = new ScheduledJobsService(orderService, customerInvoiceRepository, supplierInvoiceRepository, auditLogService);
        ReflectionTestUtils.setField(service, "reservationExpiryMinutes", 60);
    }

    @Test
    void expireStaleReservationsRecordsAnAuditEntryWhenOrdersWereExpired() {
        when(orderService.expireStalePendingOrders(any(LocalDateTime.class))).thenReturn(3);

        service.expireStaleReservations();

        verify(orderService).expireStalePendingOrders(any(LocalDateTime.class));
        verify(auditLogService).record(eq("ORDER"), isNull(), eq(AuditAction.OTHER), eq("SYSTEM"), anyString());
    }

    @Test
    void expireStaleReservationsSkipsTheAuditEntryWhenNothingExpired() {
        when(orderService.expireStalePendingOrders(any(LocalDateTime.class))).thenReturn(0);

        service.expireStaleReservations();

        verifyNoInteractions(auditLogService);
    }

    private CustomerInvoice customerInvoice(LocalDate dueDate) {
        return CustomerInvoice.builder().id(1L).invoiceNumber("CINV-1")
                .customer(Customers.builder().id(1L).name("Wholesale Co").build())
                .invoiceDate(dueDate.minusDays(30)).dueDate(dueDate)
                .subtotalAmount(new BigDecimal("100")).taxAmount(BigDecimal.ZERO).totalAmount(new BigDecimal("100"))
                .amountPaid(BigDecimal.ZERO).status(CustomerInvoiceStatus.POSTED).build();
    }

    private SupplierInvoice supplierInvoice(LocalDate dueDate) {
        return SupplierInvoice.builder().id(1L).invoiceNumber("INV-1")
                .supplier(Suppliers.builder().id(1L).name("Acme").build())
                .invoiceDate(dueDate.minusDays(30)).dueDate(dueDate)
                .subtotalAmount(new BigDecimal("100")).taxAmount(BigDecimal.ZERO).totalAmount(new BigDecimal("100"))
                .amountPaid(BigDecimal.ZERO).status(SupplierInvoiceStatus.POSTED).build();
    }

    @Test
    void logOverdueInvoicesRecordsASummaryWhenInvoicesArePastDue() {
        when(customerInvoiceRepository.findByStatusIn(anyList()))
                .thenReturn(List.of(customerInvoice(LocalDate.now().minusDays(5))));
        when(supplierInvoiceRepository.findByStatusIn(anyList()))
                .thenReturn(List.of(supplierInvoice(LocalDate.now().minusDays(2))));

        service.logOverdueInvoices();

        verify(auditLogService).record(eq("OVERDUE_INVOICE_CHECK"), isNull(), eq(AuditAction.OTHER), eq("SYSTEM"), anyString());
    }

    @Test
    void logOverdueInvoicesSkipsTheAuditEntryWhenNothingIsOverdue() {
        when(customerInvoiceRepository.findByStatusIn(anyList()))
                .thenReturn(List.of(customerInvoice(LocalDate.now().plusDays(10))));
        when(supplierInvoiceRepository.findByStatusIn(anyList()))
                .thenReturn(List.of());

        service.logOverdueInvoices();

        verifyNoInteractions(auditLogService);
    }
}
