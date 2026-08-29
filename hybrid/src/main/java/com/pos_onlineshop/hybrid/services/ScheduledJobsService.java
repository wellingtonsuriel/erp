package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.customerInvoice.CustomerInvoiceRepository;
import com.pos_onlineshop.hybrid.enums.AuditAction;
import com.pos_onlineshop.hybrid.enums.CustomerInvoiceStatus;
import com.pos_onlineshop.hybrid.enums.SupplierInvoiceStatus;
import com.pos_onlineshop.hybrid.supplierInvoice.SupplierInvoiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * The recurring, unattended jobs this system needs regardless of whether anyone is looking -
 * see SchedulingConfiguration for why period-close and the adjustments it wires in
 * (accrual reversal, depreciation, FX revaluation, IAS 29 restatement) are deliberately NOT
 * here: those already run automatically inside AccountingPeriodService.closePeriod(), which
 * itself stays a deliberate human action, not a blind cron.
 *
 * Both jobs here are read-mostly and idempotent by construction: expireStaleReservations()
 * only ever acts on orders still PENDING past the cutoff (an order it already cancelled
 * won't match the query again), and logOverdueInvoices() never mutates anything - it only
 * records a summary count to the audit log.
 *
 * Known limitation: logOverdueInvoices() logs a summary rather than notifying a specific
 * person, because neither CustomerInvoice nor SupplierInvoice records who "owns" it (no
 * created-by/sales-rep/buyer field exists on either entity) - NotificationService has no
 * valid recipient to route a per-invoice notification to without guessing one. The audit
 * log entry is a real, findable record of the check having run and what it found; per-user
 * notification is real follow-on work once invoice ownership is actually tracked.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduledJobsService {

    private final OrderService orderService;
    private final CustomerInvoiceRepository customerInvoiceRepository;
    private final SupplierInvoiceRepository supplierInvoiceRepository;
    private final AuditLogService auditLogService;

    @Value("${scheduled-jobs.reservation-expiry-minutes:60}")
    private int reservationExpiryMinutes;

    /** Every 15 minutes: cancels ONLINE orders still PENDING more than
     * reservationExpiryMinutes after they were placed, releasing the stock they reserved. */
    @Scheduled(cron = "${scheduled-jobs.reservation-expiry-cron:0 0/15 * * * *}")
    public void expireStaleReservations() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(reservationExpiryMinutes);
        int expired = orderService.expireStalePendingOrders(cutoff);
        if (expired > 0) {
            auditLogService.record("ORDER", null, AuditAction.OTHER, "SYSTEM",
                    "Reservation-expiry job cancelled " + expired + " stale PENDING online order(s) placed before " + cutoff);
        }
    }

    /** Once daily at 06:00: counts open customer/supplier invoices past their due date and
     * records the count to the audit log - see the class comment for why this stops short
     * of a per-user notification. */
    @Scheduled(cron = "${scheduled-jobs.overdue-invoice-check-cron:0 0 6 * * *}")
    public void logOverdueInvoices() {
        LocalDate today = LocalDate.now();
        long overdueCustomerInvoices = customerInvoiceRepository.findByStatusIn(
                        List.of(CustomerInvoiceStatus.POSTED, CustomerInvoiceStatus.PARTIALLY_PAID)).stream()
                .filter(invoice -> invoice.getDueDate() != null && invoice.getDueDate().isBefore(today))
                .count();
        long overdueSupplierInvoices = supplierInvoiceRepository.findByStatusIn(
                        List.of(SupplierInvoiceStatus.POSTED, SupplierInvoiceStatus.PARTIALLY_PAID)).stream()
                .filter(invoice -> invoice.getDueDate() != null && invoice.getDueDate().isBefore(today))
                .count();

        if (overdueCustomerInvoices > 0 || overdueSupplierInvoices > 0) {
            auditLogService.record("OVERDUE_INVOICE_CHECK", null, AuditAction.OTHER, "SYSTEM",
                    overdueCustomerInvoices + " overdue customer invoice(s), "
                            + overdueSupplierInvoices + " overdue supplier invoice(s) as of " + today);
            log.info("Overdue invoice check: {} customer, {} supplier invoice(s) overdue as of {}",
                    overdueCustomerInvoices, overdueSupplierInvoices, today);
        }
    }
}
