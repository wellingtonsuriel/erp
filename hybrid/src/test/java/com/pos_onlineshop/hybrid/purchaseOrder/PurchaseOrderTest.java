package com.pos_onlineshop.hybrid.purchaseOrder;

import com.pos_onlineshop.hybrid.cashier.Cashier;
import com.pos_onlineshop.hybrid.enums.PurchaseOrderStatus;
import com.pos_onlineshop.hybrid.products.Product;
import com.pos_onlineshop.hybrid.purchaseOrderLine.PurchaseOrderLine;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/** Pure entity/state-machine tests - no Spring context, no database. */
class PurchaseOrderTest {

    private Product product(long id) {
        return Product.builder().id(id).name("Product " + id).build();
    }

    private PurchaseOrder draftPoWithOneLine(int quantity) {
        PurchaseOrder po = PurchaseOrder.builder().poNumber("PO-TEST-1").build();
        po.addLine(PurchaseOrderLine.builder()
                .product(product(1L))
                .quantityOrdered(quantity)
                .unitCost(new BigDecimal("10.00"))
                .build());
        return po;
    }

    @Test
    void fullLifecycleTransitionsInOrder() {
        PurchaseOrder po = draftPoWithOneLine(10);
        assertEquals(PurchaseOrderStatus.DRAFT, po.getStatus());

        po.submit();
        assertEquals(PurchaseOrderStatus.SUBMITTED, po.getStatus());

        po.approve(Cashier.builder().id(1L).build());
        assertEquals(PurchaseOrderStatus.APPROVED, po.getStatus());

        po.getLines().get(0).applyReceipt(10);
        po.refreshStatusAfterReceipt();
        assertEquals(PurchaseOrderStatus.RECEIVED, po.getStatus());

        po.close();
        assertEquals(PurchaseOrderStatus.CLOSED, po.getStatus());
    }

    @Test
    void partialReceiptLeavesStatusPartiallyReceived() {
        PurchaseOrder po = draftPoWithOneLine(10);
        po.submit();
        po.approve(Cashier.builder().id(1L).build());

        po.getLines().get(0).applyReceipt(4);
        po.refreshStatusAfterReceipt();

        assertEquals(PurchaseOrderStatus.PARTIALLY_RECEIVED, po.getStatus());
        assertEquals(6, po.getLines().get(0).getOutstandingQuantity());
        assertFalse(po.getLines().get(0).isFullyReceived());
    }

    @Test
    void cannotSubmitWithoutLines() {
        PurchaseOrder po = PurchaseOrder.builder().poNumber("PO-EMPTY").build();
        assertThrows(IllegalStateException.class, po::submit);
    }

    @Test
    void cannotApproveBeforeSubmit() {
        PurchaseOrder po = draftPoWithOneLine(5);
        assertThrows(IllegalStateException.class, () -> po.approve(Cashier.builder().id(1L).build()));
    }

    @Test
    void cannotReceiveUnapprovedOrder() {
        PurchaseOrder po = draftPoWithOneLine(5);
        po.submit();
        assertFalse(po.canBeReceived());
    }

    @Test
    void cannotOverReceiveALine() {
        PurchaseOrderLine line = PurchaseOrderLine.builder()
                .product(product(1L)).quantityOrdered(5).unitCost(BigDecimal.TEN).build();

        assertThrows(IllegalArgumentException.class, () -> line.applyReceipt(6));
    }

    @Test
    void cannotReceiveNegativeOrZeroQuantity() {
        PurchaseOrderLine line = PurchaseOrderLine.builder()
                .product(product(1L)).quantityOrdered(5).unitCost(BigDecimal.TEN).build();

        assertThrows(IllegalArgumentException.class, () -> line.applyReceipt(0));
        assertThrows(IllegalArgumentException.class, () -> line.applyReceipt(-1));
    }

    @Test
    void cannotCancelAReceivedOrder() {
        PurchaseOrder po = draftPoWithOneLine(5);
        po.submit();
        po.approve(Cashier.builder().id(1L).build());
        po.getLines().get(0).applyReceipt(5);
        po.refreshStatusAfterReceipt();

        assertEquals(PurchaseOrderStatus.RECEIVED, po.getStatus());
        assertThrows(IllegalStateException.class, () -> po.cancel("changed my mind"));
    }

    @Test
    void canCancelADraftOrder() {
        PurchaseOrder po = draftPoWithOneLine(5);
        po.cancel("no longer needed");
        assertEquals(PurchaseOrderStatus.CANCELLED, po.getStatus());
    }

    @Test
    void totalValueSumsLineTotals() {
        PurchaseOrder po = PurchaseOrder.builder().poNumber("PO-MULTI").build();
        po.addLine(PurchaseOrderLine.builder().product(product(1L)).quantityOrdered(3).unitCost(new BigDecimal("10.00")).build());
        po.addLine(PurchaseOrderLine.builder().product(product(2L)).quantityOrdered(2).unitCost(new BigDecimal("25.00")).build());

        assertEquals(0, new BigDecimal("80.00").compareTo(po.getTotalValue()));
    }
}
