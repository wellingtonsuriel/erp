package com.pos_onlineshop.hybrid.enums;

/**
 * Status of fiscal document processing
 */
public enum FiscalStatus {
    PENDING,             // Awaiting fiscalisation
    PROCESSING,          // Being sent to fiscal device
    FISCALISED,          // Successfully fiscalised
    FAILED,              // Fiscalisation failed
    CANCELLED,           // Document cancelled
    VERIFIED             // Verified with ZIMRA
}
