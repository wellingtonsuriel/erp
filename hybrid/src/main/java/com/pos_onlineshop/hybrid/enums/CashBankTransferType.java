package com.pos_onlineshop.hybrid.enums;

/** Purely descriptive label on a CashBankTransfer - every value posts identically
 * (Dr toAccount / Cr fromAccount). DEPOSIT is till/cash moving into a bank account,
 * WITHDRAWAL is a bank account paying out to cash, TRANSFER is bank-to-bank or
 * wallet-to-wallet, MOBILE_MONEY_SETTLEMENT is a mobile-money wallet clearing into a
 * bank account. See CashBankTransferService. */
public enum CashBankTransferType {
    DEPOSIT,
    WITHDRAWAL,
    TRANSFER,
    MOBILE_MONEY_SETTLEMENT
}
