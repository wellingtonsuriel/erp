package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.account.Account;
import com.pos_onlineshop.hybrid.account.AccountRepository;
import com.pos_onlineshop.hybrid.customerInvoice.CustomerInvoice;
import com.pos_onlineshop.hybrid.customerInvoice.CustomerInvoiceRepository;
import com.pos_onlineshop.hybrid.customers.Customers;
import com.pos_onlineshop.hybrid.dtos.ControlAccountReconciliationReport;
import com.pos_onlineshop.hybrid.enums.AccountType;
import com.pos_onlineshop.hybrid.enums.CustomerInvoiceStatus;
import com.pos_onlineshop.hybrid.enums.DebitCredit;
import com.pos_onlineshop.hybrid.enums.SupplierInvoiceStatus;
import com.pos_onlineshop.hybrid.journalLine.JournalLineRepository;
import com.pos_onlineshop.hybrid.suppliers.Suppliers;
import com.pos_onlineshop.hybrid.supplierInvoice.SupplierInvoice;
import com.pos_onlineshop.hybrid.supplierInvoice.SupplierInvoiceRepository;
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
class ControlAccountReconciliationServiceTest {

    @Mock private AccountRepository accountRepository;
    @Mock private JournalLineRepository journalLineRepository;
    @Mock private SupplierInvoiceRepository supplierInvoiceRepository;
    @Mock private CustomerInvoiceRepository customerInvoiceRepository;
    @Mock private ShopInventoryService shopInventoryService;

    private ControlAccountReconciliationService service;

    private final LocalDate asOfDate = LocalDate.of(2026, 8, 31);
    private Account accountsPayable;
    private Account accountsReceivable;
    private Account inventoryAsset;

    @BeforeEach
    void setUp() {
        service = new ControlAccountReconciliationService(accountRepository, journalLineRepository,
                supplierInvoiceRepository, customerInvoiceRepository, shopInventoryService);

        accountsPayable = Account.builder().id(1L).code("2100").name("Accounts Payable")
                .accountType(AccountType.LIABILITY).normalBalance(DebitCredit.CREDIT).controlAccount(true).active(true).build();
        accountsReceivable = Account.builder().id(2L).code("1100").name("Accounts Receivable")
                .accountType(AccountType.ASSET).normalBalance(DebitCredit.DEBIT).controlAccount(true).active(true).build();
        inventoryAsset = Account.builder().id(3L).code("1200").name("Inventory Asset")
                .accountType(AccountType.ASSET).normalBalance(DebitCredit.DEBIT).controlAccount(true).active(true).build();

        when(accountRepository.findByCode("2100")).thenReturn(java.util.Optional.of(accountsPayable));
        when(accountRepository.findByCode("1100")).thenReturn(java.util.Optional.of(accountsReceivable));
        when(accountRepository.findByCode("1200")).thenReturn(java.util.Optional.of(inventoryAsset));
    }

    private SupplierInvoice supplierInvoice(BigDecimal total, BigDecimal paid) {
        return SupplierInvoice.builder().id(1L).invoiceNumber("INV-1")
                .supplier(Suppliers.builder().id(1L).name("Acme").build())
                .invoiceDate(LocalDate.now()).dueDate(LocalDate.now().plusDays(30))
                .subtotalAmount(total).taxAmount(BigDecimal.ZERO).totalAmount(total).amountPaid(paid)
                .status(SupplierInvoiceStatus.PARTIALLY_PAID).build();
    }

    private CustomerInvoice customerInvoice(BigDecimal total, BigDecimal paid) {
        return CustomerInvoice.builder().id(1L).invoiceNumber("CINV-1")
                .customer(Customers.builder().id(1L).name("Wholesale Co").build())
                .invoiceDate(LocalDate.now()).dueDate(LocalDate.now().plusDays(30))
                .subtotalAmount(total).taxAmount(BigDecimal.ZERO).totalAmount(total).amountPaid(paid)
                .status(CustomerInvoiceStatus.PARTIALLY_PAID).build();
    }

    @Test
    void reportsAMatchedAccountsPayableLineWhenGlAndSubledgerAgree() {
        when(journalLineRepository.aggregateBeforeDate(asOfDate.plusDays(1), null)).thenReturn(List.<Object[]>of(
                new Object[]{1L, BigDecimal.ZERO, new BigDecimal("500.00")}));
        when(supplierInvoiceRepository.findByStatusIn(List.of(SupplierInvoiceStatus.POSTED, SupplierInvoiceStatus.PARTIALLY_PAID)))
                .thenReturn(List.of(supplierInvoice(new BigDecimal("500.00"), BigDecimal.ZERO)));
        when(customerInvoiceRepository.findByStatusIn(List.of(CustomerInvoiceStatus.POSTED, CustomerInvoiceStatus.PARTIALLY_PAID)))
                .thenReturn(List.of());
        when(shopInventoryService.calculateTotalInventoryValue()).thenReturn(BigDecimal.ZERO);

        ControlAccountReconciliationReport report = service.generate(asOfDate);

        ControlAccountReconciliationReport.Line apLine = report.getLines().stream()
                .filter(l -> "2100".equals(l.getAccountCode())).findFirst().orElseThrow();
        assertTrue(apLine.isMatched());
        assertEquals(0, BigDecimal.ZERO.compareTo(apLine.getVariance()));
    }

    @Test
    void reportsAVarianceRatherThanSilentlyAdjustingIt() {
        when(journalLineRepository.aggregateBeforeDate(asOfDate.plusDays(1), null)).thenReturn(List.<Object[]>of(
                new Object[]{1L, BigDecimal.ZERO, new BigDecimal("500.00")}));
        when(supplierInvoiceRepository.findByStatusIn(List.of(SupplierInvoiceStatus.POSTED, SupplierInvoiceStatus.PARTIALLY_PAID)))
                .thenReturn(List.of(supplierInvoice(new BigDecimal("450.00"), BigDecimal.ZERO)));
        when(customerInvoiceRepository.findByStatusIn(List.of(CustomerInvoiceStatus.POSTED, CustomerInvoiceStatus.PARTIALLY_PAID)))
                .thenReturn(List.of());
        when(shopInventoryService.calculateTotalInventoryValue()).thenReturn(BigDecimal.ZERO);

        ControlAccountReconciliationReport report = service.generate(asOfDate);

        ControlAccountReconciliationReport.Line apLine = report.getLines().stream()
                .filter(l -> "2100".equals(l.getAccountCode())).findFirst().orElseThrow();
        assertFalse(apLine.isMatched());
        assertEquals(0, new BigDecimal("50.00").compareTo(apLine.getVariance()));
    }

    @Test
    void accountsReceivableUsesDebitNormalBalance() {
        when(journalLineRepository.aggregateBeforeDate(asOfDate.plusDays(1), null)).thenReturn(List.<Object[]>of(
                new Object[]{2L, new BigDecimal("300.00"), BigDecimal.ZERO}));
        when(supplierInvoiceRepository.findByStatusIn(List.of(SupplierInvoiceStatus.POSTED, SupplierInvoiceStatus.PARTIALLY_PAID)))
                .thenReturn(List.of());
        when(customerInvoiceRepository.findByStatusIn(List.of(CustomerInvoiceStatus.POSTED, CustomerInvoiceStatus.PARTIALLY_PAID)))
                .thenReturn(List.of(customerInvoice(new BigDecimal("300.00"), BigDecimal.ZERO)));
        when(shopInventoryService.calculateTotalInventoryValue()).thenReturn(BigDecimal.ZERO);

        ControlAccountReconciliationReport report = service.generate(asOfDate);

        ControlAccountReconciliationReport.Line arLine = report.getLines().stream()
                .filter(l -> "1100".equals(l.getAccountCode())).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("300.00").compareTo(arLine.getGlBalance()));
        assertTrue(arLine.isMatched());
    }

    @Test
    void inventoryLineUsesTheAuthoritativeSharedValuationNotAnImmutableLotSnapshot() {
        when(journalLineRepository.aggregateBeforeDate(asOfDate.plusDays(1), null)).thenReturn(List.<Object[]>of(
                new Object[]{3L, new BigDecimal("200.00"), BigDecimal.ZERO}));
        when(supplierInvoiceRepository.findByStatusIn(List.of(SupplierInvoiceStatus.POSTED, SupplierInvoiceStatus.PARTIALLY_PAID)))
                .thenReturn(List.of());
        when(customerInvoiceRepository.findByStatusIn(List.of(CustomerInvoiceStatus.POSTED, CustomerInvoiceStatus.PARTIALLY_PAID)))
                .thenReturn(List.of());
        // e.g. 100 received, 60 sold via POS -> InventoryTotal reflects 40 remaining, valued
        // by the shared ShopInventoryService method (not by summing never-decremented
        // ShopInventory receipt-lot quantities, which would still show all 100).
        when(shopInventoryService.calculateTotalInventoryValue()).thenReturn(new BigDecimal("200.00"));

        ControlAccountReconciliationReport report = service.generate(asOfDate);

        ControlAccountReconciliationReport.Line invLine = report.getLines().stream()
                .filter(l -> "1200".equals(l.getAccountCode())).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("200.00").compareTo(invLine.getSubledgerBalance()));
        assertTrue(invLine.isMatched());
        assertNotNull(invLine.getNote());
    }

    @Test
    void inventoryVarianceIsReportedWhenGlAndAuthoritativeValuationDisagree() {
        when(journalLineRepository.aggregateBeforeDate(asOfDate.plusDays(1), null)).thenReturn(List.<Object[]>of(
                new Object[]{3L, new BigDecimal("1000.00"), BigDecimal.ZERO}));
        when(supplierInvoiceRepository.findByStatusIn(List.of(SupplierInvoiceStatus.POSTED, SupplierInvoiceStatus.PARTIALLY_PAID)))
                .thenReturn(List.of());
        when(customerInvoiceRepository.findByStatusIn(List.of(CustomerInvoiceStatus.POSTED, CustomerInvoiceStatus.PARTIALLY_PAID)))
                .thenReturn(List.of());
        // Received 100 units worth 1000 in the GL, but only 400 worth remains on hand (60 sold).
        when(shopInventoryService.calculateTotalInventoryValue()).thenReturn(new BigDecimal("400.00"));

        ControlAccountReconciliationReport report = service.generate(asOfDate);

        ControlAccountReconciliationReport.Line invLine = report.getLines().stream()
                .filter(l -> "1200".equals(l.getAccountCode())).findFirst().orElseThrow();
        assertFalse(invLine.isMatched());
        assertEquals(0, new BigDecimal("600.00").compareTo(invLine.getVariance()));
    }
}
