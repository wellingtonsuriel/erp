package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.account.Account;
import com.pos_onlineshop.hybrid.account.AccountRepository;
import com.pos_onlineshop.hybrid.cashier.Cashier;
import com.pos_onlineshop.hybrid.cashier.CashierRepository;
import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.currency.CurrencyRepository;
import com.pos_onlineshop.hybrid.dtos.RecordSupplierPaymentRequest;
import com.pos_onlineshop.hybrid.enums.GLSourceModule;
import com.pos_onlineshop.hybrid.enums.PaymentMethod;
import com.pos_onlineshop.hybrid.gl.ManualLineSpec;
import com.pos_onlineshop.hybrid.journalEntry.JournalEntry;
import com.pos_onlineshop.hybrid.supplierInvoice.SupplierInvoice;
import com.pos_onlineshop.hybrid.supplierInvoice.SupplierInvoiceRepository;
import com.pos_onlineshop.hybrid.supplierPayment.SupplierPayment;
import com.pos_onlineshop.hybrid.supplierPayment.SupplierPaymentRepository;
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
 * Records a payment against exactly one SupplierInvoice. Multi-invoice allocation (a single
 * payment applied across several invoices) is not implemented - each SupplierPayment
 * references one invoice; a payment covering several invoices today needs one SupplierPayment
 * call per invoice. Documented as a limitation rather than modeled partially.
 *
 * Posts via GLPostingService.postManual() rather than the SUPPLIER_PAYMENT/SUPPLIER_PAYMENT_CASH
 * PostingRule, specifically to recognize realized FX gain/loss - the AP leg is valued at the
 * rate SupplierInvoiceService booked the invoice at (SupplierInvoice.exchangeRate, relieving
 * the liability at exactly the value it was recorded at), the Cash/Bank leg at today's
 * settlement rate (the real economic value paid). See CustomerReceiptService's identical
 * reasoning and class comment for why this only became correct to build once JournalValidator
 * started balancing entries in base currency.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SupplierPaymentService {

    private static final String FX_GAIN_LOSS_ACCOUNT_CODE = "5900";
    private static final String ACCOUNTS_PAYABLE_ACCOUNT_CODE = "2100";

    private final SupplierPaymentRepository supplierPaymentRepository;
    private final SupplierInvoiceRepository supplierInvoiceRepository;
    private final CurrencyRepository currencyRepository;
    private final CashierRepository cashierRepository;
    private final AccountRepository accountRepository;
    private final GLPostingService glPostingService;
    private final CurrencyService currencyService;

    @Transactional
    public SupplierPayment recordPayment(RecordSupplierPaymentRequest request) {
        SupplierInvoice invoice = supplierInvoiceRepository.findById(request.getInvoiceId())
                .orElseThrow(() -> new IllegalArgumentException("Supplier invoice not found: " + request.getInvoiceId()));
        Currency currency = currencyRepository.findById(request.getCurrencyId())
                .orElseThrow(() -> new IllegalArgumentException("Currency not found: " + request.getCurrencyId()));

        Cashier recordedBy = null;
        if (request.getRecordedById() != null) {
            recordedBy = cashierRepository.findById(request.getRecordedById())
                    .orElseThrow(() -> new IllegalArgumentException("Cashier not found: " + request.getRecordedById()));
        }

        // Throws IllegalArgumentException if this would overpay the invoice, IllegalStateException
        // if the invoice can't currently receive payment (DRAFT, already PAID, or VOID).
        invoice.applyPayment(request.getAmount());
        supplierInvoiceRepository.save(invoice);

        SupplierPayment payment = SupplierPayment.builder()
                .supplier(invoice.getSupplier())
                .invoice(invoice)
                .amount(request.getAmount())
                .currency(currency)
                .paymentMethod(request.getPaymentMethod())
                .paymentDate(request.getPaymentDate() != null ? request.getPaymentDate() : LocalDate.now())
                .reference(request.getReference())
                .recordedBy(recordedBy)
                .notes(request.getNotes())
                .build();
        SupplierPayment savedPayment = supplierPaymentRepository.save(payment);

        postPaymentToGeneralLedger(savedPayment, invoice);

        log.info("Recorded payment of {} {} against supplier invoice {} - now {}",
                request.getAmount(), currency.getCode(), invoice.getInvoiceNumber(), invoice.getStatus());
        return savedPayment;
    }

    private void postPaymentToGeneralLedger(SupplierPayment payment, SupplierInvoice invoice) {
        Currency currency = payment.getCurrency();
        Currency baseCurrency = currencyService.getBaseCurrency();
        BigDecimal settlementRate = BigDecimal.ONE;
        if (currency != null && !currency.equals(baseCurrency)) {
            settlementRate = currencyService.getExchangeRate(currency, baseCurrency);
        }
        BigDecimal bookedRate = invoice.getExchangeRate() != null ? invoice.getExchangeRate() : BigDecimal.ONE;

        String cashAccountCode = payment.getPaymentMethod() == PaymentMethod.CASH ? "1010" : "1030";
        Account cashAccount = accountRepository.findByCode(cashAccountCode)
                .orElseThrow(() -> new IllegalStateException("Chart of accounts is missing " + cashAccountCode));
        Account accountsPayable = accountRepository.findByCode(ACCOUNTS_PAYABLE_ACCOUNT_CODE)
                .orElseThrow(() -> new IllegalStateException("Chart of accounts is missing " + ACCOUNTS_PAYABLE_ACCOUNT_CODE));

        String memo = "Payment against invoice " + invoice.getInvoiceNumber() + " (" + invoice.getSupplier().getName() + ")";
        List<ManualLineSpec> specs = new ArrayList<>();
        specs.add(new ManualLineSpec(accountsPayable, payment.getAmount(), BigDecimal.ZERO, currency, bookedRate, invoice.getShop(), memo));
        specs.add(new ManualLineSpec(cashAccount, BigDecimal.ZERO, payment.getAmount(), currency, settlementRate, invoice.getShop(), memo));

        BigDecimal apBase = payment.getAmount().multiply(bookedRate).setScale(4, RoundingMode.HALF_UP);
        BigDecimal cashBase = payment.getAmount().multiply(settlementRate).setScale(4, RoundingMode.HALF_UP);
        BigDecimal diff = apBase.subtract(cashBase);
        if (diff.compareTo(BigDecimal.ZERO) != 0) {
            Account fxAccount = accountRepository.findByCode(FX_GAIN_LOSS_ACCOUNT_CODE)
                    .orElseThrow(() -> new IllegalStateException("Chart of accounts is missing " + FX_GAIN_LOSS_ACCOUNT_CODE));
            if (diff.compareTo(BigDecimal.ZERO) > 0) {
                // The liability was relieved for more base-currency value than was actually
                // paid in cash - a realized gain.
                specs.add(new ManualLineSpec(fxAccount, BigDecimal.ZERO, diff, baseCurrency, BigDecimal.ONE, invoice.getShop(), "Realized FX gain on payment"));
            } else {
                specs.add(new ManualLineSpec(fxAccount, diff.negate(), BigDecimal.ZERO, baseCurrency, BigDecimal.ONE, invoice.getShop(), "Realized FX loss on payment"));
            }
        }

        JournalEntry entry = glPostingService.postManual(
                "SUPPLIER-PAYMENT-" + payment.getId(),
                payment.getPaymentDate(),
                memo,
                GLSourceModule.INVENTORY,
                "SUPPLIER_PAYMENT",
                payment.getId(),
                specs,
                payment.getRecordedBy() != null ? payment.getRecordedBy().getFullName() : "system");
        log.info("Payment {} posted as GL entry #{}", payment.getId(), entry.getEntryNumber());
    }
}
