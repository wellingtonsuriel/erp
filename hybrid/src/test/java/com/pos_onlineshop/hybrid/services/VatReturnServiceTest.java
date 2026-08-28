package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.account.Account;
import com.pos_onlineshop.hybrid.account.AccountRepository;
import com.pos_onlineshop.hybrid.dtos.VatReturnReport;
import com.pos_onlineshop.hybrid.enums.AccountType;
import com.pos_onlineshop.hybrid.enums.DebitCredit;
import com.pos_onlineshop.hybrid.journalLine.JournalLineRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VatReturnServiceTest {

    @Mock private AccountRepository accountRepository;
    @Mock private JournalLineRepository journalLineRepository;

    private VatReturnService service;

    private final LocalDate from = LocalDate.of(2026, 8, 1);
    private final LocalDate to = LocalDate.of(2026, 8, 31);

    private Account vatOutput;
    private Account vatInput;
    private Account accountsPayable;
    private Account posRevenue;

    @BeforeEach
    void setUp() {
        service = new VatReturnService(accountRepository, journalLineRepository);

        vatOutput = Account.builder().id(1L).code("2200").name("VAT Output / Payable")
                .accountType(AccountType.LIABILITY).normalBalance(DebitCredit.CREDIT).active(true).build();
        vatInput = Account.builder().id(2L).code("1400").name("VAT Input / Recoverable")
                .accountType(AccountType.ASSET).normalBalance(DebitCredit.DEBIT).active(true).build();
        accountsPayable = Account.builder().id(3L).code("2100").name("Accounts Payable")
                .accountType(AccountType.LIABILITY).normalBalance(DebitCredit.CREDIT).controlAccount(true).active(true).build();
        posRevenue = Account.builder().id(4L).code("4000").name("Sales Revenue - POS")
                .accountType(AccountType.REVENUE).normalBalance(DebitCredit.CREDIT).active(true).build();
    }

    @Test
    void computesOutputInputAndNetVatFromActualGlMovements() {
        when(accountRepository.findByActiveTrue()).thenReturn(List.of(vatOutput, vatInput, accountsPayable, posRevenue));
        when(journalLineRepository.aggregateBetween(from, to, null)).thenReturn(List.<Object[]>of(
                new Object[]{1L, BigDecimal.ZERO, new BigDecimal("150.00")},          // VAT output: credit 150
                new Object[]{2L, new BigDecimal("40.00"), BigDecimal.ZERO},           // VAT input: debit 40
                new Object[]{3L, BigDecimal.ZERO, new BigDecimal("300.00")},          // AP: credit 300 (gross purchases)
                new Object[]{4L, BigDecimal.ZERO, new BigDecimal("1000.00")}));       // revenue: credit 1000

        VatReturnReport report = service.generate(from, to, null);

        assertEquals(0, new BigDecimal("150.00").compareTo(report.getOutputTax()));
        assertEquals(0, new BigDecimal("40.00").compareTo(report.getInputTax()));
        assertEquals(0, new BigDecimal("110.00").compareTo(report.getNetTaxPayable()));
        assertEquals(0, new BigDecimal("1000.00").compareTo(report.getTaxableSales()));
        // Taxable purchases = gross purchases 300 - input tax 40 = 260
        assertEquals(0, new BigDecimal("260.00").compareTo(report.getTaxablePurchases()));
    }

    @Test
    void negativeNetTaxIndicatesARefundablePosition() {
        when(accountRepository.findByActiveTrue()).thenReturn(List.of(vatOutput, vatInput));
        when(journalLineRepository.aggregateBetween(from, to, null)).thenReturn(List.<Object[]>of(
                new Object[]{1L, BigDecimal.ZERO, new BigDecimal("50.00")},
                new Object[]{2L, new BigDecimal("80.00"), BigDecimal.ZERO}));

        VatReturnReport report = service.generate(from, to, null);

        assertEquals(0, new BigDecimal("-30.00").compareTo(report.getNetTaxPayable()));
    }

    @Test
    void exemptAndZeroRatedSalesAreExplicitlyNullNotFabricatedAsZero() {
        when(accountRepository.findByActiveTrue()).thenReturn(List.of(vatOutput, vatInput));
        when(journalLineRepository.aggregateBetween(from, to, null)).thenReturn(List.of());

        VatReturnReport report = service.generate(from, to, null);

        assertNull(report.getExemptSales());
        assertNull(report.getZeroRatedSales());
    }

    @Test
    void handlesNoTaxActivityInThePeriod() {
        when(accountRepository.findByActiveTrue()).thenReturn(List.of(vatOutput, vatInput, accountsPayable, posRevenue));
        when(journalLineRepository.aggregateBetween(from, to, null)).thenReturn(List.of());

        VatReturnReport report = service.generate(from, to, null);

        assertEquals(0, BigDecimal.ZERO.compareTo(report.getOutputTax()));
        assertEquals(0, BigDecimal.ZERO.compareTo(report.getInputTax()));
        assertEquals(0, BigDecimal.ZERO.compareTo(report.getNetTaxPayable()));
    }
}
