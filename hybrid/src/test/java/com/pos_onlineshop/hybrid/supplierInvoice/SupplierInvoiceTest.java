package com.pos_onlineshop.hybrid.supplierInvoice;

import com.pos_onlineshop.hybrid.enums.SupplierInvoiceStatus;
import com.pos_onlineshop.hybrid.purchaseOrder.PurchaseOrder;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/** Pure entity/state-machine tests - no Spring context, no database. */
class SupplierInvoiceTest {

    private SupplierInvoice draftInvoice(String total) {
        return SupplierInvoice.builder()
                .invoiceNumber("INV-1")
                .invoiceDate(LocalDate.now())
                .dueDate(LocalDate.now().plusDays(30))
                .subtotalAmount(new BigDecimal(total))
                .taxAmount(BigDecimal.ZERO)
                .totalAmount(new BigDecimal(total))
                .build();
    }

    @Test
    void postTransitionsDraftToPosted() {
        SupplierInvoice invoice = draftInvoice("100.00");
        invoice.post();
        assertEquals(SupplierInvoiceStatus.POSTED, invoice.getStatus());
    }

    @Test
    void cannotPostTwice() {
        SupplierInvoice invoice = draftInvoice("100.00");
        invoice.post();
        assertThrows(IllegalStateException.class, invoice::post);
    }

    @Test
    void fullPaymentMarksInvoicePaid() {
        SupplierInvoice invoice = draftInvoice("100.00");
        invoice.post();
        invoice.applyPayment(new BigDecimal("100.00"));

        assertEquals(SupplierInvoiceStatus.PAID, invoice.getStatus());
        assertEquals(0, BigDecimal.ZERO.compareTo(invoice.getOutstandingAmount()));
    }

    @Test
    void partialPaymentMarksInvoicePartiallyPaid() {
        SupplierInvoice invoice = draftInvoice("100.00");
        invoice.post();
        invoice.applyPayment(new BigDecimal("40.00"));

        assertEquals(SupplierInvoiceStatus.PARTIALLY_PAID, invoice.getStatus());
        assertEquals(0, new BigDecimal("60.00").compareTo(invoice.getOutstandingAmount()));
    }

    @Test
    void overpaymentIsRejected() {
        SupplierInvoice invoice = draftInvoice("100.00");
        invoice.post();
        invoice.applyPayment(new BigDecimal("60.00"));

        assertThrows(IllegalArgumentException.class, () -> invoice.applyPayment(new BigDecimal("60.00")));
        // the rejected payment must not have partially applied
        assertEquals(0, new BigDecimal("40.00").compareTo(invoice.getOutstandingAmount()));
    }

    @Test
    void cannotPayAnUnpostedInvoice() {
        SupplierInvoice invoice = draftInvoice("100.00");
        assertThrows(IllegalStateException.class, () -> invoice.applyPayment(new BigDecimal("10.00")));
    }

    @Test
    void cannotVoidAPostedInvoiceWithPaymentsApplied() {
        SupplierInvoice invoice = draftInvoice("100.00");
        invoice.post();
        invoice.applyPayment(new BigDecimal("10.00"));

        assertThrows(IllegalStateException.class, () -> invoice.voidInvoice("changed my mind"));
    }

    @Test
    void canVoidAPostedInvoiceWithNoPayments() {
        SupplierInvoice invoice = draftInvoice("100.00");
        invoice.post();
        invoice.voidInvoice("duplicate entry");

        assertEquals(SupplierInvoiceStatus.VOID, invoice.getStatus());
        assertEquals("duplicate entry", invoice.getVoidedReason());
    }

    @Test
    void isPoLinkedReflectsPresenceOfPurchaseOrder() {
        SupplierInvoice standalone = draftInvoice("50.00");
        assertFalse(standalone.isPoLinked());

        SupplierInvoice poLinked = draftInvoice("50.00");
        poLinked.setPurchaseOrder(PurchaseOrder.builder().id(1L).poNumber("PO-1").build());
        assertTrue(poLinked.isPoLinked());
    }
}
