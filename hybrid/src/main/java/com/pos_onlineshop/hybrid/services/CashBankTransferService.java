package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.account.Account;
import com.pos_onlineshop.hybrid.account.AccountRepository;
import com.pos_onlineshop.hybrid.bankAccount.BankAccount;
import com.pos_onlineshop.hybrid.bankAccount.BankAccountRepository;
import com.pos_onlineshop.hybrid.cashBankTransfer.CashBankTransfer;
import com.pos_onlineshop.hybrid.cashBankTransfer.CashBankTransferRepository;
import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.dtos.CashBankTransferResponse;
import com.pos_onlineshop.hybrid.dtos.CreateCashBankTransferRequest;
import com.pos_onlineshop.hybrid.enums.GLSourceModule;
import com.pos_onlineshop.hybrid.gl.ManualLineSpec;
import com.pos_onlineshop.hybrid.journalEntry.JournalEntry;
import com.pos_onlineshop.hybrid.userAccount.UserAccount;
import com.pos_onlineshop.hybrid.userAccount.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Deposits, withdrawals, bank-to-bank/wallet transfers, and mobile-money settlement all
 * post through here as a single mechanism (Dr toAccount / Cr fromAccount) - see
 * CashBankTransfer's class comment for why that unification is safe. currentBalance on
 * both accounts is debited/credited in the same transaction as the GL posting, keeping the
 * subledger and the journal entry from ever disagreeing. Both accounts must share the same
 * currency - this posts a single amount to both sides, not a currency exchange. When that
 * currency isn't the base currency, the real rate (not an assumed 1:1) is looked up so
 * JournalLine.baseAmount stays correct for balance validation and reporting.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CashBankTransferService {

    private final CashBankTransferRepository cashBankTransferRepository;
    private final BankAccountRepository bankAccountRepository;
    private final UserAccountRepository userAccountRepository;
    private final AccountRepository accountRepository;
    private final GLPostingService glPostingService;
    private final CurrencyService currencyService;

    @Transactional(readOnly = true)
    public List<CashBankTransferResponse> findAll() {
        return cashBankTransferRepository.findAllByOrderByIdDesc().stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional
    public CashBankTransferResponse createTransfer(CreateCashBankTransferRequest request) {
        if (cashBankTransferRepository.existsByReferenceNumber(request.getReferenceNumber())) {
            throw new IllegalArgumentException("A transfer with reference " + request.getReferenceNumber() + " already exists");
        }
        if (request.getFromAccountId().equals(request.getToAccountId())) {
            throw new IllegalArgumentException("Source and destination accounts must be different");
        }
        BankAccount fromAccount = bankAccountRepository.findById(request.getFromAccountId())
                .orElseThrow(() -> new IllegalArgumentException("Source account not found: " + request.getFromAccountId()));
        BankAccount toAccount = bankAccountRepository.findById(request.getToAccountId())
                .orElseThrow(() -> new IllegalArgumentException("Destination account not found: " + request.getToAccountId()));
        if (fromAccount.getCurrentBalance().compareTo(request.getAmount()) < 0) {
            throw new IllegalStateException("Insufficient balance in " + fromAccount.getAccountName());
        }
        if (!fromAccount.getCurrency().getId().equals(toAccount.getCurrency().getId())) {
            throw new IllegalArgumentException("Cannot transfer between accounts in different currencies ("
                    + fromAccount.getCurrency().getCode() + " -> " + toAccount.getCurrency().getCode()
                    + ") - this is not a currency exchange");
        }
        Currency currency = fromAccount.getCurrency();
        UserAccount createdBy = resolveUser(request.getCreatedByUserId());

        CashBankTransfer transfer = CashBankTransfer.builder()
                .referenceNumber(request.getReferenceNumber())
                .transferType(request.getTransferType())
                .fromAccount(fromAccount)
                .toAccount(toAccount)
                .amount(request.getAmount())
                .currency(currency)
                .transferDate(request.getTransferDate())
                .description(request.getDescription())
                .createdBy(createdBy)
                .build();
        CashBankTransfer saved = cashBankTransferRepository.save(transfer);

        JournalEntry entry = postToGeneralLedger(saved);
        saved.setJournalEntry(entry);

        fromAccount.setCurrentBalance(fromAccount.getCurrentBalance().subtract(request.getAmount()));
        toAccount.setCurrentBalance(toAccount.getCurrentBalance().add(request.getAmount()));
        bankAccountRepository.save(fromAccount);
        bankAccountRepository.save(toAccount);

        CashBankTransfer finalTransfer = cashBankTransferRepository.save(saved);
        log.info("Cash/bank transfer {} ({}) posted: {} -> {} for {} {} - GL entry #{}",
                finalTransfer.getReferenceNumber(), finalTransfer.getTransferType(),
                fromAccount.getAccountName(), toAccount.getAccountName(),
                request.getAmount(), currency.getCode(), entry.getEntryNumber());
        return toResponse(finalTransfer);
    }

    private JournalEntry postToGeneralLedger(CashBankTransfer transfer) {
        BankAccount fromAccount = transfer.getFromAccount();
        BankAccount toAccount = transfer.getToAccount();
        Account fromGlAccount = accountRepository.findByCode(fromAccount.getGlAccountCode())
                .orElseThrow(() -> new IllegalStateException("Chart of accounts is missing " + fromAccount.getGlAccountCode()));
        Account toGlAccount = accountRepository.findByCode(toAccount.getGlAccountCode())
                .orElseThrow(() -> new IllegalStateException("Chart of accounts is missing " + toAccount.getGlAccountCode()));

        String memo = transfer.getTransferType() + " " + transfer.getReferenceNumber()
                + " (" + fromAccount.getAccountName() + " -> " + toAccount.getAccountName() + ")";
        Currency currency = transfer.getCurrency();
        BigDecimal exchangeRate = exchangeRateToBase(currency);
        List<ManualLineSpec> specs = List.of(
                new ManualLineSpec(toGlAccount, transfer.getAmount(), BigDecimal.ZERO, currency, exchangeRate, toAccount.getShop(), memo),
                new ManualLineSpec(fromGlAccount, BigDecimal.ZERO, transfer.getAmount(), currency, exchangeRate, fromAccount.getShop(), memo));

        return glPostingService.postManual(
                "CASHBANK-TRANSFER-" + transfer.getReferenceNumber(), transfer.getTransferDate(), memo,
                GLSourceModule.TRANSFER, "CASH_BANK_TRANSFER", transfer.getId(), specs, transfer.getCreatedBy().getUsername());
    }

    private BigDecimal exchangeRateToBase(Currency currency) {
        Currency baseCurrency = currencyService.getBaseCurrency();
        return currency == null || currency.equals(baseCurrency)
                ? BigDecimal.ONE : currencyService.getExchangeRate(currency, baseCurrency);
    }

    private UserAccount resolveUser(Long userId) {
        return userAccountRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
    }

    private CashBankTransferResponse toResponse(CashBankTransfer transfer) {
        return CashBankTransferResponse.builder()
                .id(transfer.getId())
                .referenceNumber(transfer.getReferenceNumber())
                .transferType(transfer.getTransferType().name())
                .fromAccountId(transfer.getFromAccount().getId())
                .fromAccountName(transfer.getFromAccount().getAccountName())
                .toAccountId(transfer.getToAccount().getId())
                .toAccountName(transfer.getToAccount().getAccountName())
                .amount(transfer.getAmount())
                .currencyCode(transfer.getCurrency() != null ? transfer.getCurrency().getCode() : null)
                .transferDate(transfer.getTransferDate())
                .description(transfer.getDescription())
                .createdById(transfer.getCreatedBy() != null ? transfer.getCreatedBy().getId() : null)
                .createdByUsername(transfer.getCreatedBy() != null ? transfer.getCreatedBy().getUsername() : null)
                .createdAt(transfer.getCreatedAt())
                .journalEntryId(transfer.getJournalEntry() != null ? transfer.getJournalEntry().getId() : null)
                .journalEntryNumber(transfer.getJournalEntry() != null ? transfer.getJournalEntry().getEntryNumber() : null)
                .build();
    }
}
