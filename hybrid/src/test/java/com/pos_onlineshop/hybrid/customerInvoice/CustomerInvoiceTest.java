package com.pos_onlineshop.hybrid.customerInvoice;

import com.pos_onlineshop.hybrid.enums.CustomerInvoiceStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/** Pure entity/state-machine tests - no Spring context, no database. */
class CustomerInvoiceTest {

    private CustomerInvoice draftInvoice(String total) {
        return CustomerInvoice.builder()
                .invoiceNumber("CINV-1")
                .invoiceDate(LocalDate.now())
                .dueDate(LocalDate.now().plusDays(30))
                .subtotalAmount(new BigDecimal(total))
                .taxAmount(BigDecimal.ZERO)
                .totalAmount(new BigDecimal(total))
                .build();
    }

    @Test
    void postTransitionsDraftToPosted() {
        CustomerInvoice invoice = draftInvoice("100.00");
        invoice.post();
        assertEquals(CustomerInvoiceStatus.POSTED, invoice.getStatus());
    }

    @Test
    void cannotPostTwice() {
        CustomerInvoice invoice = draftInvoice("100.00");
        invoice.post();
        assertThrows(IllegalStateException.class, invoice::post);
    }

    @Test
    void fullPaymentMarksInvoicePaid() {
        CustomerInvoice invoice = draftInvoice("100.00");
        invoice.post();
        invoice.applyPayment(new BigDecimal("100.00"));

        assertEquals(CustomerInvoiceStatus.PAID, invoice.getStatus());
        assertEquals(0, BigDecimal.ZERO.compareTo(invoice.getOutstandingAmount()));
    }

    @Test
    void partialPaymentMarksInvoicePartiallyPaid() {
        CustomerInvoice invoice = draftInvoice("100.00");
        invoice.post();
        invoice.applyPayment(new BigDecimal("40.00"));

        assertEquals(CustomerInvoiceStatus.PARTIALLY_PAID, invoice.getStatus());
        assertEquals(0, new BigDecimal("60.00").compareTo(invoice.getOutstandingAmount()));
    }

    @Test
    void overpaymentIsRejected() {
        CustomerInvoice invoice = draftInvoice("100.00");
        invoice.post();
        invoice.applyPayment(new BigDecimal("60.00"));

        assertThrows(IllegalArgumentException.class, () -> invoice.applyPayment(new BigDecimal("60.00")));
        assertEquals(0, new BigDecimal("40.00").compareTo(invoice.getOutstandingAmount()));
    }

    @Test
    void cannotPayAnUnpostedInvoice() {
        CustomerInvoice invoice = draftInvoice("100.00");
        assertThrows(IllegalStateException.class, () -> invoice.applyPayment(new BigDecimal("10.00")));
    }

    @Test
    void cannotVoidAPostedInvoiceWithPaymentsApplied() {
        CustomerInvoice invoice = draftInvoice("100.00");
        invoice.post();
        invoice.applyPayment(new BigDecimal("10.00"));

        assertThrows(IllegalStateException.class, () -> invoice.voidInvoice("changed my mind"));
    }

    @Test
    void canVoidAPostedInvoiceWithNoPayments() {
        CustomerInvoice invoice = draftInvoice("100.00");
        invoice.post();
        invoice.voidInvoice("duplicate entry");

        assertEquals(CustomerInvoiceStatus.VOID, invoice.getStatus());
        assertEquals("duplicate entry", invoice.getVoidedReason());
    }
}
