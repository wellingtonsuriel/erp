package com.pos_onlineshop.hybrid.enums;

/**
 * Types of fiscal documents recognized by ZIMRA
 */
public enum FiscalDocumentType {
    FISCAL_INVOICE,      // Standard fiscal invoice
    FISCAL_RECEIPT,      // POS receipt
    CREDIT_NOTE,         // Credit note for returns/refunds
    DEBIT_NOTE,          // Debit note for additional charges
    PROFORMA_INVOICE,    // Proforma invoice (non-fiscal)
    TAX_INVOICE          // Tax invoice for VAT registered customers
}
