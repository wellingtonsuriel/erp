package com.pos_onlineshop.hybrid.enums;

/**
 * Types of fiscal devices certified by ZIMRA
 */
public enum FiscalDeviceType {
    FISCAL_PRINTER,      // Physical fiscal printer
    SDC_DEVICE,          // Sales Data Controller device
    VIRTUAL_DEVICE,      // Virtual/software fiscal device
    CLOUD_FISCAL_API     // Cloud-based fiscalisation API
}
