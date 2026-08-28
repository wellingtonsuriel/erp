package com.pos_onlineshop.hybrid.enums;

/**
 * Business events the GL Posting Engine knows how to turn into a balanced journal entry.
 * Emitted today: POS_CASH_SALE/POS_NON_CASH_SALE/SALE_REFUND (POSService), STOCK_RECEIPT
 * (ShopInventoryService, also reached via PurchaseOrderService.receive), INVENTORY_TRANSFER/
 * DAMAGED_STOCK (InventoryTransferService), ONLINE_ORDER_PAID (OrderService),
 * SESSION_CASH_SHORT/SESSION_CASH_OVER (CashierService), PURCHASE_INVOICE (SupplierInvoiceService
 * - only for invoices with no linked PurchaseOrder, see SupplierInvoiceService's class comment
 * for why), SUPPLIER_PAYMENT (SupplierPaymentService). The remaining values have seeded
 * PostingRules ready to receive events once the corresponding integration is implemented.
 */
public enum FinancialEventType {
    POS_CASH_SALE,
    POS_NON_CASH_SALE,
    ONLINE_ORDER_PAID,
    ONLINE_ORDER_UNPAID,
    SALE_REFUND,
    STOCK_RECEIPT,
    INVENTORY_TRANSFER,
    DAMAGED_STOCK,
    SESSION_CASH_SHORT,
    SESSION_CASH_OVER,
    LOYALTY_REDEMPTION,
    FX_REVALUATION,
    PURCHASE_INVOICE,
    /** Supplier payment settled via bank (the default for a B2B payment). */
    SUPPLIER_PAYMENT,
    /** Supplier payment settled from the physical cash drawer - distinct from SUPPLIER_PAYMENT
     * so it credits 1010 Cash rather than 1030 Bank, mirroring the POS cash/non-cash split. */
    SUPPLIER_PAYMENT_CASH,
    /** A standalone credit-sale invoice, independent of the POS/online Order pipeline (which
     * has no credit/unpaid concept today - see CustomerInvoiceService). */
    CUSTOMER_INVOICE,
    /** Customer receipt via mobile money/card clearing - the default, matching how customers
     * actually pay in this business. */
    CUSTOMER_RECEIPT,
    /** Customer receipt via physical cash. */
    CUSTOMER_RECEIPT_CASH,
    MANUAL_ENTRY
}
