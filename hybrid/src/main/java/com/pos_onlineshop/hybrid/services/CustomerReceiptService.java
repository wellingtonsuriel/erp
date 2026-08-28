package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.cashier.Cashier;
import com.pos_onlineshop.hybrid.cashier.CashierRepository;
import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.currency.CurrencyRepository;
import com.pos_onlineshop.hybrid.customerInvoice.CustomerInvoice;
import com.pos_onlineshop.hybrid.customerInvoice.CustomerInvoiceRepository;
import com.pos_onlineshop.hybrid.customerReceipt.CustomerReceipt;
import com.pos_onlineshop.hybrid.customerReceipt.CustomerReceiptRepository;
import com.pos_onlineshop.hybrid.dtos.RecordCustomerReceiptRequest;
import com.pos_onlineshop.hybrid.enums.FinancialEventType;
import com.pos_onlineshop.hybrid.enums.GLSourceModule;
import com.pos_onlineshop.hybrid.enums.PaymentMethod;
import com.pos_onlineshop.hybrid.gl.FinancialEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Records a receipt against exactly one CustomerInvoice. Multi-invoice allocation is not
 * implemented - see SupplierPaymentService's equivalent note.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerReceiptService {

    private final CustomerReceiptRepository customerReceiptRepository;
    private final CustomerInvoiceRepository customerInvoiceRepository;
    private final CurrencyRepository currencyRepository;
    private final CashierRepository cashierRepository;
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
        BigDecimal exchangeRate = BigDecimal.ONE;
        if (currency != null && !currency.equals(baseCurrency)) {
            exchangeRate = currencyService.getExchangeRate(currency, baseCurrency);
        }

        FinancialEvent event = FinancialEvent.builder()
                .eventType(receipt.getPaymentMethod() == PaymentMethod.CASH
                        ? FinancialEventType.CUSTOMER_RECEIPT_CASH
                        : FinancialEventType.CUSTOMER_RECEIPT)
                .sourceModule(GLSourceModule.ORDER)
                .sourceReferenceType("CUSTOMER_RECEIPT")
                .sourceReferenceId(receipt.getId())
                .idempotencyKey("CUSTOMER-RECEIPT-" + receipt.getId())
                .eventDate(receipt.getReceiptDate())
                .description("Receipt against invoice " + invoice.getInvoiceNumber() + " (" + invoice.getCustomer().getName() + ")")
                .shop(invoice.getShop())
                .currency(currency)
                .exchangeRate(exchangeRate)
                .grossAmount(receipt.getAmount())
                .netAmount(receipt.getAmount())
                .postedBy(receipt.getRecordedBy() != null ? receipt.getRecordedBy().getFullName() : "system")
                .build();

        glPostingService.post(event);
    }
}
