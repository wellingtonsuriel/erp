package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.account.Account;
import com.pos_onlineshop.hybrid.account.AccountRepository;
import com.pos_onlineshop.hybrid.cashier.Cashier;
import com.pos_onlineshop.hybrid.cashier.CashierRepository;
import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.currency.CurrencyRepository;
import com.pos_onlineshop.hybrid.customerInvoice.CustomerInvoice;
import com.pos_onlineshop.hybrid.customerInvoice.CustomerInvoiceRepository;
import com.pos_onlineshop.hybrid.customerReceipt.CustomerReceipt;
import com.pos_onlineshop.hybrid.customerReceipt.CustomerReceiptRepository;
import com.pos_onlineshop.hybrid.dtos.RecordCustomerReceiptRequest;
import com.pos_onlineshop.hybrid.enums.GLSourceModule;
import com.pos_onlineshop.hybrid.enums.PaymentMethod;
import com.pos_onlineshop.hybrid.gl.ManualLineSpec;
import com.pos_onlineshop.hybrid.journalEntry.JournalEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Records a receipt against exactly one CustomerInvoice. Multi-invoice allocation is not
 * implemented - see SupplierPaymentService's equivalent note.
 *
 * Posts via GLPostingService.postManual() rather than the CUSTOMER_RECEIPT/CUSTOMER_RECEIPT_CASH
 * PostingRule, specifically to recognize realized FX gain/loss: the Cash/Card leg is valued
 * at today's settlement rate (the real economic value received), while the AR leg is valued
 * at the rate CustomerInvoiceService booked the invoice at (CustomerInvoice.exchangeRate -
 * so AR is relieved at exactly the value it was recorded at, not at whatever rate happens to
 * be current today). Any difference between those two base-currency values is the realized
 * gain/loss on settling this receivable in a currency whose rate moved between invoice and
 * receipt, posted to 5900 FX Gain/Loss. This only became correct to build once
 * JournalValidator started balancing entries in base currency rather than raw transaction-
 * currency amounts (see that class's comment) - a genuine FX line has no raw-currency
 * counterpart to balance against.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerReceiptService {

    private static final String FX_GAIN_LOSS_ACCOUNT_CODE = "5900";
    private static final String ACCOUNTS_RECEIVABLE_ACCOUNT_CODE = "1100";

    private final CustomerReceiptRepository customerReceiptRepository;
    private final CustomerInvoiceRepository customerInvoiceRepository;
    private final CurrencyRepository currencyRepository;
    private final CashierRepository cashierRepository;
    private final AccountRepository accountRepository;
    private final GLPostingService glPostingService;
    private final CurrencyService currencyService;

    @Transactional
    public CustomerReceipt recordReceipt(RecordCustomerReceiptRequest request) {
        CustomerInvoice invoice = customerInvoiceRepository.findById(request.getInvoiceId())
                .orElseThrow(() -> new IllegalArgumentException("Customer invoice not found: " + request.getInvoiceId()));
        Currency currency = currencyRepository.findById(request.getCurrencyId())
                .orElseThrow(() -> new IllegalArgumentException("Currency not found: " + request.getCurrencyId()));

        Cashier recordedBy = null;
        if (request.getRecordedById() != null) {
            recordedBy = cashierRepository.findById(request.getRecordedById())
                    .orElseThrow(() -> new IllegalArgumentException("Cashier not found: " + request.getRecordedById()));
        }

        // Throws IllegalArgumentException if this would overpay the invoice, IllegalStateException
        // if the invoice can't currently receive payment.
        invoice.applyPayment(request.getAmount());
        customerInvoiceRepository.save(invoice);

        CustomerReceipt receipt = CustomerReceipt.builder()
                .customer(invoice.getCustomer())
                .invoice(invoice)
                .amount(request.getAmount())
                .currency(currency)
                .paymentMethod(request.getPaymentMethod())
                .receiptDate(request.getReceiptDate() != null ? request.getReceiptDate() : LocalDate.now())
                .reference(request.getReference())
                .recordedBy(recordedBy)
                .notes(request.getNotes())
                .build();
        CustomerReceipt savedReceipt = customerReceiptRepository.save(receipt);

        postToGeneralLedger(savedReceipt, invoice);

        log.info("Recorded receipt of {} {} against customer invoice {} - now {}",
                request.getAmount(), currency.getCode(), invoice.getInvoiceNumber(), invoice.getStatus());
        return savedReceipt;
    }

    private void postToGeneralLedger(CustomerReceipt receipt, CustomerInvoice invoice) {
        Currency currency = receipt.getCurrency();
        Currency baseCurrency = currencyService.getBaseCurrency();
        BigDecimal settlementRate = BigDecimal.ONE;
        if (currency != null && !currency.equals(baseCurrency)) {
            settlementRate = currencyService.getExchangeRate(currency, baseCurrency);
        }
        BigDecimal bookedRate = invoice.getExchangeRate() != null ? invoice.getExchangeRate() : BigDecimal.ONE;

        String cashAccountCode = receipt.getPaymentMethod() == PaymentMethod.CASH ? "1010" : "1020";
        Account cashAccount = accountRepository.findByCode(cashAccountCode)
                .orElseThrow(() -> new IllegalStateException("Chart of accounts is missing " + cashAccountCode));
        Account accountsReceivable = accountRepository.findByCode(ACCOUNTS_RECEIVABLE_ACCOUNT_CODE)
                .orElseThrow(() -> new IllegalStateException("Chart of accounts is missing " + ACCOUNTS_RECEIVABLE_ACCOUNT_CODE));

        String memo = "Receipt against invoice " + invoice.getInvoiceNumber() + " (" + invoice.getCustomer().getName() + ")";
        List<ManualLineSpec> specs = new ArrayList<>();
        specs.add(new ManualLineSpec(cashAccount, receipt.getAmount(), BigDecimal.ZERO, currency, settlementRate, invoice.getShop(), memo));
        specs.add(new ManualLineSpec(accountsReceivable, BigDecimal.ZERO, receipt.getAmount(), currency, bookedRate, invoice.getShop(), memo));

        BigDecimal cashBase = receipt.getAmount().multiply(settlementRate).setScale(4, RoundingMode.HALF_UP);
        BigDecimal arBase = receipt.getAmount().multiply(bookedRate).setScale(4, RoundingMode.HALF_UP);
        BigDecimal diff = cashBase.subtract(arBase);
        if (diff.compareTo(BigDecimal.ZERO) != 0) {
            Account fxAccount = accountRepository.findByCode(FX_GAIN_LOSS_ACCOUNT_CODE)
                    .orElseThrow(() -> new IllegalStateException("Chart of accounts is missing " + FX_GAIN_LOSS_ACCOUNT_CODE));
            if (diff.compareTo(BigDecimal.ZERO) > 0) {
                // Received more base-currency value than AR was booked at - a realized gain.
                specs.add(new ManualLineSpec(fxAccount, BigDecimal.ZERO, diff, baseCurrency, BigDecimal.ONE, invoice.getShop(), "Realized FX gain on receipt"));
            } else {
                specs.add(new ManualLineSpec(fxAccount, diff.negate(), BigDecimal.ZERO, baseCurrency, BigDecimal.ONE, invoice.getShop(), "Realized FX loss on receipt"));
            }
        }

        JournalEntry entry = glPostingService.postManual(
                "CUSTOMER-RECEIPT-" + receipt.getId(),
                receipt.getReceiptDate(),
                memo,
                GLSourceModule.ORDER,
                "CUSTOMER_RECEIPT",
                receipt.getId(),
                specs,
                receipt.getRecordedBy() != null ? receipt.getRecordedBy().getFullName() : "system");
        log.info("Receipt {} posted as GL entry #{}", receipt.getId(), entry.getEntryNumber());
    }
}
