package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.account.Account;
import com.pos_onlineshop.hybrid.account.AccountRepository;
import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.customerInvoice.CustomerInvoice;
import com.pos_onlineshop.hybrid.customerInvoice.CustomerInvoiceRepository;
import com.pos_onlineshop.hybrid.dtos.FxRevaluationResponse;
import com.pos_onlineshop.hybrid.enums.CustomerInvoiceStatus;
import com.pos_onlineshop.hybrid.enums.FxInvoiceType;
import com.pos_onlineshop.hybrid.enums.GLSourceModule;
import com.pos_onlineshop.hybrid.enums.SupplierInvoiceStatus;
import com.pos_onlineshop.hybrid.fxRevaluation.FxRevaluationEntry;
import com.pos_onlineshop.hybrid.fxRevaluation.FxRevaluationEntryRepository;
import com.pos_onlineshop.hybrid.gl.ManualLineSpec;
import com.pos_onlineshop.hybrid.journalEntry.JournalEntry;
import com.pos_onlineshop.hybrid.supplierInvoice.SupplierInvoice;
import com.pos_onlineshop.hybrid.supplierInvoice.SupplierInvoiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Unrealized FX: period-end (or on-demand) revaluation of every still-open foreign-currency
 * CustomerInvoice/SupplierInvoice balance to the current exchange rate - the IAS 21 monetary-
 * item restatement realized FX (CustomerReceiptService/SupplierPaymentService) doesn't cover,
 * since those only recognize a gain/loss at settlement, not on a balance still outstanding at
 * period end.
 *
 * "Avoid repeated revaluation errors" (never compounding the same rate movement twice): each
 * invoice's own exchangeRate field IS the rate its GL balance is currently carried at - not
 * fixed at the original booking rate. Every revaluation diffs the outstanding balance against
 * THAT field (whatever it currently holds - the original booking rate the first time, or the
 * previous revaluation's rate every time after), posts only the incremental movement since
 * then, and then advances invoice.exchangeRate to the new rate. This is also exactly why
 * CustomerReceiptService/SupplierPaymentService's realized-FX calculation composes correctly
 * with this without any special-casing: it already reads whatever rate the invoice is
 * CURRENTLY carried at, which this service is what keeps up to date between invoice and
 * eventual settlement.
 *
 * FxRevaluationEntry is a pure audit record (this service does not read it back to compute
 * anything) - it exists so "why did this invoice's exchangeRate change, and by how much GL
 * impact" has a real, queryable answer instead of only being inferable from GL entries.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class FxRevaluationService {

    private static final String FX_GAIN_LOSS_ACCOUNT_CODE = "5900";
    private static final String ACCOUNTS_RECEIVABLE_ACCOUNT_CODE = "1100";
    private static final String ACCOUNTS_PAYABLE_ACCOUNT_CODE = "2100";

    private final CustomerInvoiceRepository customerInvoiceRepository;
    private final SupplierInvoiceRepository supplierInvoiceRepository;
    private final FxRevaluationEntryRepository fxRevaluationEntryRepository;
    private final AccountRepository accountRepository;
    private final GLPostingService glPostingService;
    private final CurrencyService currencyService;

    public List<FxRevaluationResponse> findAll() {
        return fxRevaluationEntryRepository.findAllByOrderByIdDesc().stream()
                .map(this::toResponse).toList();
    }

    /** Revalues every open (POSTED/PARTIALLY_PAID) foreign-currency customer and supplier
     * invoice to today's rate, dated revaluationDate. Skips an invoice whose rate hasn't
     * moved since it was last carried at (no-op, no GL noise for an unchanged rate) and one
     * whose outstanding balance is already zero. Returns only the revaluations actually
     * posted. */
    public List<FxRevaluationResponse> revalueOpenBalances(LocalDate revaluationDate, String performedBy) {
        Currency baseCurrency = currencyService.getBaseCurrency();
        List<FxRevaluationResponse> results = new ArrayList<>();

        for (CustomerInvoice invoice : customerInvoiceRepository.findByStatusIn(
                List.of(CustomerInvoiceStatus.POSTED, CustomerInvoiceStatus.PARTIALLY_PAID))) {
            revalueCustomerInvoice(invoice, baseCurrency, revaluationDate, performedBy).ifPresent(results::add);
        }
        for (SupplierInvoice invoice : supplierInvoiceRepository.findByStatusIn(
                List.of(SupplierInvoiceStatus.POSTED, SupplierInvoiceStatus.PARTIALLY_PAID))) {
            revalueSupplierInvoice(invoice, baseCurrency, revaluationDate, performedBy).ifPresent(results::add);
        }
        log.info("FX revaluation as of {} posted {} adjustment(s)", revaluationDate, results.size());
        return results;
    }

    private Optional<FxRevaluationResponse> revalueCustomerInvoice(
            CustomerInvoice invoice, Currency baseCurrency, LocalDate revaluationDate, String performedBy) {
        Currency currency = invoice.getCurrency();
        BigDecimal outstanding = invoice.getOutstandingAmount();
        if (currency == null || currency.equals(baseCurrency) || outstanding.compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.empty();
        }
        BigDecimal priorRate = invoice.getExchangeRate() != null ? invoice.getExchangeRate() : BigDecimal.ONE;
        BigDecimal currentRate = currencyService.getExchangeRate(currency, baseCurrency);
        BigDecimal diff = outstanding.multiply(currentRate.subtract(priorRate)).setScale(4, RoundingMode.HALF_UP);
        if (diff.compareTo(BigDecimal.ZERO) == 0) {
            return Optional.empty();
        }

        Account accountsReceivable = accountRepository.findByCode(ACCOUNTS_RECEIVABLE_ACCOUNT_CODE)
                .orElseThrow(() -> new IllegalStateException("Chart of accounts is missing " + ACCOUNTS_RECEIVABLE_ACCOUNT_CODE));
        Account fxAccount = accountRepository.findByCode(FX_GAIN_LOSS_ACCOUNT_CODE)
                .orElseThrow(() -> new IllegalStateException("Chart of accounts is missing " + FX_GAIN_LOSS_ACCOUNT_CODE));

        String memo = "Unrealized FX revaluation of invoice " + invoice.getInvoiceNumber()
                + " (" + invoice.getCustomer().getName() + ") as of " + revaluationDate;
        BigDecimal magnitude = diff.abs();
        List<ManualLineSpec> specs;
        if (diff.compareTo(BigDecimal.ZERO) > 0) {
            // The receivable is worth more in base currency now than it was carried at - a gain.
            specs = List.of(
                    new ManualLineSpec(accountsReceivable, magnitude, BigDecimal.ZERO, baseCurrency, BigDecimal.ONE, invoice.getShop(), memo),
                    new ManualLineSpec(fxAccount, BigDecimal.ZERO, magnitude, baseCurrency, BigDecimal.ONE, invoice.getShop(), "Unrealized FX gain"));
        } else {
            specs = List.of(
                    new ManualLineSpec(accountsReceivable, BigDecimal.ZERO, magnitude, baseCurrency, BigDecimal.ONE, invoice.getShop(), memo),
                    new ManualLineSpec(fxAccount, magnitude, BigDecimal.ZERO, baseCurrency, BigDecimal.ONE, invoice.getShop(), "Unrealized FX loss"));
        }

        JournalEntry entry = glPostingService.postManual(
                "FX-REVAL-CUSTOMER-" + invoice.getId() + "-" + revaluationDate,
                revaluationDate, memo, GLSourceModule.SYSTEM, "FX_REVALUATION", invoice.getId(), specs, performedBy);

        invoice.setExchangeRate(currentRate);
        customerInvoiceRepository.save(invoice);

        FxRevaluationEntry saved = fxRevaluationEntryRepository.save(FxRevaluationEntry.builder()
                .invoiceType(FxInvoiceType.CUSTOMER)
                .invoiceId(invoice.getId())
                .invoiceNumber(invoice.getInvoiceNumber())
                .revaluationDate(revaluationDate)
                .priorRate(priorRate)
                .newRate(currentRate)
                .outstandingAmount(outstanding)
                .unrealizedGainLoss(diff)
                .postedJournalEntry(entry)
                .build());
        return Optional.of(toResponse(saved));
    }

    private Optional<FxRevaluationResponse> revalueSupplierInvoice(
            SupplierInvoice invoice, Currency baseCurrency, LocalDate revaluationDate, String performedBy) {
        Currency currency = invoice.getCurrency();
        BigDecimal outstanding = invoice.getOutstandingAmount();
        if (currency == null || currency.equals(baseCurrency) || outstanding.compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.empty();
        }
        BigDecimal priorRate = invoice.getExchangeRate() != null ? invoice.getExchangeRate() : BigDecimal.ONE;
        BigDecimal currentRate = currencyService.getExchangeRate(currency, baseCurrency);
        BigDecimal diff = outstanding.multiply(currentRate.subtract(priorRate)).setScale(4, RoundingMode.HALF_UP);
        if (diff.compareTo(BigDecimal.ZERO) == 0) {
            return Optional.empty();
        }

        Account accountsPayable = accountRepository.findByCode(ACCOUNTS_PAYABLE_ACCOUNT_CODE)
                .orElseThrow(() -> new IllegalStateException("Chart of accounts is missing " + ACCOUNTS_PAYABLE_ACCOUNT_CODE));
        Account fxAccount = accountRepository.findByCode(FX_GAIN_LOSS_ACCOUNT_CODE)
                .orElseThrow(() -> new IllegalStateException("Chart of accounts is missing " + FX_GAIN_LOSS_ACCOUNT_CODE));

        String memo = "Unrealized FX revaluation of invoice " + invoice.getInvoiceNumber()
                + " (" + invoice.getSupplier().getName() + ") as of " + revaluationDate;
        BigDecimal magnitude = diff.abs();
        List<ManualLineSpec> specs;
        // Unlike AR, a positive diff (the liability is now worth more base currency) is a LOSS.
        BigDecimal unrealizedGainLoss;
        if (diff.compareTo(BigDecimal.ZERO) > 0) {
            specs = List.of(
                    new ManualLineSpec(fxAccount, magnitude, BigDecimal.ZERO, baseCurrency, BigDecimal.ONE, invoice.getShop(), "Unrealized FX loss"),
                    new ManualLineSpec(accountsPayable, BigDecimal.ZERO, magnitude, baseCurrency, BigDecimal.ONE, invoice.getShop(), memo));
            unrealizedGainLoss = diff.negate();
        } else {
            specs = List.of(
                    new ManualLineSpec(accountsPayable, magnitude, BigDecimal.ZERO, baseCurrency, BigDecimal.ONE, invoice.getShop(), memo),
                    new ManualLineSpec(fxAccount, BigDecimal.ZERO, magnitude, baseCurrency, BigDecimal.ONE, invoice.getShop(), "Unrealized FX gain"));
            unrealizedGainLoss = diff.negate();
        }

        JournalEntry entry = glPostingService.postManual(
                "FX-REVAL-SUPPLIER-" + invoice.getId() + "-" + revaluationDate,
                revaluationDate, memo, GLSourceModule.SYSTEM, "FX_REVALUATION", invoice.getId(), specs, performedBy);

        invoice.setExchangeRate(currentRate);
        supplierInvoiceRepository.save(invoice);

        FxRevaluationEntry saved = fxRevaluationEntryRepository.save(FxRevaluationEntry.builder()
                .invoiceType(FxInvoiceType.SUPPLIER)
                .invoiceId(invoice.getId())
                .invoiceNumber(invoice.getInvoiceNumber())
                .revaluationDate(revaluationDate)
                .priorRate(priorRate)
                .newRate(currentRate)
                .outstandingAmount(outstanding)
                .unrealizedGainLoss(unrealizedGainLoss)
                .postedJournalEntry(entry)
                .build());
        return Optional.of(toResponse(saved));
    }

    private FxRevaluationResponse toResponse(FxRevaluationEntry entry) {
        return FxRevaluationResponse.builder()
                .id(entry.getId())
                .invoiceType(entry.getInvoiceType().name())
                .invoiceId(entry.getInvoiceId())
                .invoiceNumber(entry.getInvoiceNumber())
                .revaluationDate(entry.getRevaluationDate())
                .priorRate(entry.getPriorRate())
                .newRate(entry.getNewRate())
                .outstandingAmount(entry.getOutstandingAmount())
                .unrealizedGainLoss(entry.getUnrealizedGainLoss())
                .createdAt(entry.getCreatedAt())
                .postedJournalEntryId(entry.getPostedJournalEntry().getId())
                .postedJournalEntryNumber(entry.getPostedJournalEntry().getEntryNumber())
                .build();
    }
}
