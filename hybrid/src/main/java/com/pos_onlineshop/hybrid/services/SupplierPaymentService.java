package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.cashier.Cashier;
import com.pos_onlineshop.hybrid.cashier.CashierRepository;
import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.currency.CurrencyRepository;
import com.pos_onlineshop.hybrid.dtos.RecordSupplierPaymentRequest;
import com.pos_onlineshop.hybrid.enums.FinancialEventType;
import com.pos_onlineshop.hybrid.enums.GLSourceModule;
import com.pos_onlineshop.hybrid.enums.PaymentMethod;
import com.pos_onlineshop.hybrid.gl.FinancialEvent;
import com.pos_onlineshop.hybrid.supplierInvoice.SupplierInvoice;
import com.pos_onlineshop.hybrid.supplierInvoice.SupplierInvoiceRepository;
import com.pos_onlineshop.hybrid.supplierPayment.SupplierPayment;
import com.pos_onlineshop.hybrid.supplierPayment.SupplierPaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Records a payment against exactly one SupplierInvoice. Multi-invoice allocation (a single
 * payment applied across several invoices) is not implemented - each SupplierPayment
 * references one invoice; a payment covering several invoices today needs one SupplierPayment
 * call per invoice. Documented as a limitation rather than modeled partially.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SupplierPaymentService {

    private final SupplierPaymentRepository supplierPaymentRepository;
    private final SupplierInvoiceRepository supplierInvoiceRepository;
    private final CurrencyRepository currencyRepository;
    private final CashierRepository cashierRepository;
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
        BigDecimal exchangeRate = BigDecimal.ONE;
        if (currency != null && !currency.equals(baseCurrency)) {
            exchangeRate = currencyService.getExchangeRate(currency, baseCurrency);
        }

        FinancialEvent event = FinancialEvent.builder()
                .eventType(payment.getPaymentMethod() == PaymentMethod.CASH
                        ? FinancialEventType.SUPPLIER_PAYMENT_CASH
                        : FinancialEventType.SUPPLIER_PAYMENT)
                .sourceModule(GLSourceModule.INVENTORY)
                .sourceReferenceType("SUPPLIER_PAYMENT")
                .sourceReferenceId(payment.getId())
                .idempotencyKey("SUPPLIER-PAYMENT-" + payment.getId())
                .eventDate(payment.getPaymentDate())
                .description("Payment against invoice " + invoice.getInvoiceNumber() + " (" + invoice.getSupplier().getName() + ")")
                .shop(invoice.getShop())
                .currency(currency)
                .exchangeRate(exchangeRate)
                .grossAmount(payment.getAmount())
                .netAmount(payment.getAmount())
                .postedBy(payment.getRecordedBy() != null ? payment.getRecordedBy().getFullName() : "system")
                .build();

        glPostingService.post(event);
    }
}
