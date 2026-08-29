package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.account.Account;
import com.pos_onlineshop.hybrid.account.AccountRepository;
import com.pos_onlineshop.hybrid.bankAccount.BankAccount;
import com.pos_onlineshop.hybrid.bankAccount.BankAccountRepository;
import com.pos_onlineshop.hybrid.bankCharge.BankCharge;
import com.pos_onlineshop.hybrid.bankCharge.BankChargeRepository;
import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.dtos.BankChargeResponse;
import com.pos_onlineshop.hybrid.dtos.CreateBankChargeRequest;
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
 * A fee the bank deducted directly from a BankAccount - see BankCharge's class comment.
 * Posted immediately (Dr 5500 Bank Charges / Cr the account's control GL code) since there
 * is no approval workflow for a charge the bank has already taken.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BankChargeService {

    private static final String BANK_CHARGES_EXPENSE_ACCOUNT_CODE = "5500";

    private final BankChargeRepository bankChargeRepository;
    private final BankAccountRepository bankAccountRepository;
    private final UserAccountRepository userAccountRepository;
    private final AccountRepository accountRepository;
    private final GLPostingService glPostingService;

    @Transactional(readOnly = true)
    public List<BankChargeResponse> findAll() {
        return bankChargeRepository.findAllByOrderByIdDesc().stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional
    public BankChargeResponse createCharge(CreateBankChargeRequest request) {
        if (bankChargeRepository.existsByReferenceNumber(request.getReferenceNumber())) {
            throw new IllegalArgumentException("A bank charge with reference " + request.getReferenceNumber() + " already exists");
        }
        BankAccount bankAccount = bankAccountRepository.findById(request.getBankAccountId())
                .orElseThrow(() -> new IllegalArgumentException("Bank account not found: " + request.getBankAccountId()));
        if (bankAccount.getCurrentBalance().compareTo(request.getAmount()) < 0) {
            throw new IllegalStateException("Insufficient balance in " + bankAccount.getAccountName());
        }
        UserAccount createdBy = resolveUser(request.getCreatedByUserId());

        BankCharge charge = BankCharge.builder()
                .referenceNumber(request.getReferenceNumber())
                .bankAccount(bankAccount)
                .amount(request.getAmount())
                .currency(bankAccount.getCurrency())
                .chargeDate(request.getChargeDate())
                .description(request.getDescription())
                .createdBy(createdBy)
                .build();
        BankCharge saved = bankChargeRepository.save(charge);

        JournalEntry entry = postToGeneralLedger(saved);
        saved.setJournalEntry(entry);

        bankAccount.setCurrentBalance(bankAccount.getCurrentBalance().subtract(request.getAmount()));
        bankAccountRepository.save(bankAccount);

        BankCharge finalCharge = bankChargeRepository.save(saved);
        log.info("Bank charge {} of {} {} posted against {} - GL entry #{}",
                finalCharge.getReferenceNumber(), request.getAmount(), bankAccount.getCurrency().getCode(),
                bankAccount.getAccountName(), entry.getEntryNumber());
        return toResponse(finalCharge);
    }

    private JournalEntry postToGeneralLedger(BankCharge charge) {
        BankAccount bankAccount = charge.getBankAccount();
        Account bankChargesExpense = accountRepository.findByCode(BANK_CHARGES_EXPENSE_ACCOUNT_CODE)
                .orElseThrow(() -> new IllegalStateException("Chart of accounts is missing " + BANK_CHARGES_EXPENSE_ACCOUNT_CODE));
        Account bankGlAccount = accountRepository.findByCode(bankAccount.getGlAccountCode())
                .orElseThrow(() -> new IllegalStateException("Chart of accounts is missing " + bankAccount.getGlAccountCode()));

        String memo = "Bank charge " + charge.getReferenceNumber() + " on " + bankAccount.getAccountName();
        Currency currency = charge.getCurrency();
        List<ManualLineSpec> specs = List.of(
                new ManualLineSpec(bankChargesExpense, charge.getAmount(), BigDecimal.ZERO, currency, BigDecimal.ONE, bankAccount.getShop(), memo),
                new ManualLineSpec(bankGlAccount, BigDecimal.ZERO, charge.getAmount(), currency, BigDecimal.ONE, bankAccount.getShop(), memo));

        return glPostingService.postManual(
                "BANK-CHARGE-" + charge.getReferenceNumber(), charge.getChargeDate(), memo,
                GLSourceModule.SYSTEM, "BANK_CHARGE", charge.getId(), specs, charge.getCreatedBy().getUsername());
    }

    private UserAccount resolveUser(Long userId) {
        return userAccountRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
    }

    private BankChargeResponse toResponse(BankCharge charge) {
        return BankChargeResponse.builder()
                .id(charge.getId())
                .referenceNumber(charge.getReferenceNumber())
                .bankAccountId(charge.getBankAccount().getId())
                .bankAccountName(charge.getBankAccount().getAccountName())
                .amount(charge.getAmount())
                .currencyCode(charge.getCurrency() != null ? charge.getCurrency().getCode() : null)
                .chargeDate(charge.getChargeDate())
                .description(charge.getDescription())
                .createdById(charge.getCreatedBy() != null ? charge.getCreatedBy().getId() : null)
                .createdByUsername(charge.getCreatedBy() != null ? charge.getCreatedBy().getUsername() : null)
                .createdAt(charge.getCreatedAt())
                .journalEntryId(charge.getJournalEntry() != null ? charge.getJournalEntry().getId() : null)
                .journalEntryNumber(charge.getJournalEntry() != null ? charge.getJournalEntry().getEntryNumber() : null)
                .build();
    }
}
