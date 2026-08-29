package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.account.Account;
import com.pos_onlineshop.hybrid.account.AccountRepository;
import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.customers.Customers;
import com.pos_onlineshop.hybrid.customers.CustomersRepository;
import com.pos_onlineshop.hybrid.dtos.LoyaltyAccountResponse;
import com.pos_onlineshop.hybrid.dtos.LoyaltyTransactionRequest;
import com.pos_onlineshop.hybrid.dtos.LoyaltyTransactionResponse;
import com.pos_onlineshop.hybrid.enums.FinancialEventType;
import com.pos_onlineshop.hybrid.enums.GLSourceModule;
import com.pos_onlineshop.hybrid.enums.LoyaltyTransactionType;
import com.pos_onlineshop.hybrid.gl.FinancialEvent;
import com.pos_onlineshop.hybrid.gl.ManualLineSpec;
import com.pos_onlineshop.hybrid.journalEntry.JournalEntry;
import com.pos_onlineshop.hybrid.loyaltyAccount.LoyaltyAccount;
import com.pos_onlineshop.hybrid.loyaltyAccount.LoyaltyAccountRepository;
import com.pos_onlineshop.hybrid.loyaltyTransaction.LoyaltyTransaction;
import com.pos_onlineshop.hybrid.loyaltyTransaction.LoyaltyTransactionRepository;
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
 * The GL-integrated loyalty/customer-deposit liability ledger - distinct from the
 * pre-existing Customers.loyaltyPoints (a plain point counter with no accounting behind it,
 * left untouched here). Every movement posts against 2300 Customer Deposits & Loyalty
 * Liability and is rejected past LoyaltyAccount.availableBalance - see
 * LoyaltyTransactionType. REDEEMED reuses the already-seeded LOYALTY_REDEMPTION
 * FinancialEvent/PostingRule (Dr 2300 / Cr 4000, i.e. points pay for a sale exactly like
 * cash would); EARNED/EXPIRED/REVERSED post directly via GLPostingService.postManual()
 * against the new 5600 Loyalty Program Expense account, since no PostingRule for those
 * exists (their accounts don't vary per invocation the way REDEEMED's would need to if the
 * revenue account it credits ever did). Known limitation: tracked in base currency only -
 * there is no per-customer currency on this ledger.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LoyaltyService {

    private static final String LOYALTY_LIABILITY_ACCOUNT_CODE = "2300";
    private static final String LOYALTY_EXPENSE_ACCOUNT_CODE = "5600";

    private final LoyaltyAccountRepository loyaltyAccountRepository;
    private final LoyaltyTransactionRepository loyaltyTransactionRepository;
    private final CustomersRepository customersRepository;
    private final UserAccountRepository userAccountRepository;
    private final AccountRepository accountRepository;
    private final GLPostingService glPostingService;
    private final CurrencyService currencyService;

    @Transactional(readOnly = true)
    public LoyaltyAccountResponse getAccount(Long customerId) {
        Customers customer = resolveCustomer(customerId);
        LoyaltyAccount account = loyaltyAccountRepository.findByCustomerId(customerId)
                .orElseGet(() -> LoyaltyAccount.builder().customer(customer).build());
        return toAccountResponse(account);
    }

    @Transactional(readOnly = true)
    public List<LoyaltyTransactionResponse> getTransactions(Long customerId) {
        LoyaltyAccount account = loyaltyAccountRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new IllegalArgumentException("No loyalty account for customer: " + customerId));
        return loyaltyTransactionRepository.findByLoyaltyAccountOrderByIdDesc(account).stream()
                .map(this::toTransactionResponse).collect(Collectors.toList());
    }

    @Transactional
    public LoyaltyTransactionResponse earn(LoyaltyTransactionRequest request) {
        LoyaltyAccount account = getOrCreateAccount(request.getCustomerId());
        account.setAvailableBalance(account.getAvailableBalance().add(request.getAmount()));
        account.setTotalEarned(account.getTotalEarned().add(request.getAmount()));
        LoyaltyAccount savedAccount = loyaltyAccountRepository.save(account);

        LoyaltyTransaction transaction = buildTransaction(savedAccount, LoyaltyTransactionType.EARNED, request);
        LoyaltyTransaction savedTransaction = loyaltyTransactionRepository.save(transaction);

        Account expense = accountRepository.findByCode(LOYALTY_EXPENSE_ACCOUNT_CODE)
                .orElseThrow(() -> new IllegalStateException("Chart of accounts is missing " + LOYALTY_EXPENSE_ACCOUNT_CODE));
        Account liability = accountRepository.findByCode(LOYALTY_LIABILITY_ACCOUNT_CODE)
                .orElseThrow(() -> new IllegalStateException("Chart of accounts is missing " + LOYALTY_LIABILITY_ACCOUNT_CODE));
        String memo = "Loyalty earned for " + savedAccount.getCustomer().getName() + " - " + request.getDescription();
        JournalEntry entry = glPostingService.postManual("LOYALTY-EARN-" + savedTransaction.getId(), request.getTransactionDate(), memo,
                GLSourceModule.SYSTEM, "LOYALTY_TRANSACTION", savedTransaction.getId(),
                List.of(new ManualLineSpec(expense, request.getAmount(), BigDecimal.ZERO, currencyService.getBaseCurrency(), BigDecimal.ONE, null, memo),
                        new ManualLineSpec(liability, BigDecimal.ZERO, request.getAmount(), currencyService.getBaseCurrency(), BigDecimal.ONE, null, memo)),
                savedTransaction.getCreatedBy().getUsername());

        return finishTransaction(savedTransaction, entry);
    }

    @Transactional
    public LoyaltyTransactionResponse redeem(LoyaltyTransactionRequest request) {
        LoyaltyAccount account = requireExistingAccount(request.getCustomerId());
        requireSufficientBalance(account, request.getAmount());
        account.setAvailableBalance(account.getAvailableBalance().subtract(request.getAmount()));
        account.setTotalRedeemed(account.getTotalRedeemed().add(request.getAmount()));
        LoyaltyAccount savedAccount = loyaltyAccountRepository.save(account);

        LoyaltyTransaction transaction = buildTransaction(savedAccount, LoyaltyTransactionType.REDEEMED, request);
        LoyaltyTransaction savedTransaction = loyaltyTransactionRepository.save(transaction);

        Currency baseCurrency = currencyService.getBaseCurrency();
        FinancialEvent event = FinancialEvent.builder()
                .eventType(FinancialEventType.LOYALTY_REDEMPTION)
                .sourceModule(GLSourceModule.SYSTEM)
                .sourceReferenceType("LOYALTY_TRANSACTION")
                .sourceReferenceId(savedTransaction.getId())
                .idempotencyKey("LOYALTY-REDEEM-" + savedTransaction.getId())
                .eventDate(request.getTransactionDate())
                .description("Loyalty redeemed for " + savedAccount.getCustomer().getName() + " - " + request.getDescription())
                .currency(baseCurrency)
                .exchangeRate(BigDecimal.ONE)
                .grossAmount(request.getAmount())
                .netAmount(request.getAmount())
                .taxAmount(BigDecimal.ZERO)
                .postedBy(savedTransaction.getCreatedBy().getUsername())
                .build();
        JournalEntry entry = glPostingService.post(event);

        return finishTransaction(savedTransaction, entry);
    }

    @Transactional
    public LoyaltyTransactionResponse expire(LoyaltyTransactionRequest request) {
        return writeOff(request, LoyaltyTransactionType.EXPIRED);
    }

    @Transactional
    public LoyaltyTransactionResponse reverse(LoyaltyTransactionRequest request) {
        return writeOff(request, LoyaltyTransactionType.REVERSED);
    }

    private LoyaltyTransactionResponse writeOff(LoyaltyTransactionRequest request, LoyaltyTransactionType type) {
        LoyaltyAccount account = requireExistingAccount(request.getCustomerId());
        requireSufficientBalance(account, request.getAmount());
        account.setAvailableBalance(account.getAvailableBalance().subtract(request.getAmount()));
        if (type == LoyaltyTransactionType.EXPIRED) {
            account.setTotalExpired(account.getTotalExpired().add(request.getAmount()));
        } else {
            account.setTotalReversed(account.getTotalReversed().add(request.getAmount()));
        }
        LoyaltyAccount savedAccount = loyaltyAccountRepository.save(account);

        LoyaltyTransaction transaction = buildTransaction(savedAccount, type, request);
        LoyaltyTransaction savedTransaction = loyaltyTransactionRepository.save(transaction);

        Account expense = accountRepository.findByCode(LOYALTY_EXPENSE_ACCOUNT_CODE)
                .orElseThrow(() -> new IllegalStateException("Chart of accounts is missing " + LOYALTY_EXPENSE_ACCOUNT_CODE));
        Account liability = accountRepository.findByCode(LOYALTY_LIABILITY_ACCOUNT_CODE)
                .orElseThrow(() -> new IllegalStateException("Chart of accounts is missing " + LOYALTY_LIABILITY_ACCOUNT_CODE));
        String memo = "Loyalty " + type.name().toLowerCase() + " for " + savedAccount.getCustomer().getName() + " - " + request.getDescription();
        JournalEntry entry = glPostingService.postManual("LOYALTY-" + type.name() + "-" + savedTransaction.getId(), request.getTransactionDate(), memo,
                GLSourceModule.SYSTEM, "LOYALTY_TRANSACTION", savedTransaction.getId(),
                List.of(new ManualLineSpec(liability, request.getAmount(), BigDecimal.ZERO, currencyService.getBaseCurrency(), BigDecimal.ONE, null, memo),
                        new ManualLineSpec(expense, BigDecimal.ZERO, request.getAmount(), currencyService.getBaseCurrency(), BigDecimal.ONE, null, memo)),
                savedTransaction.getCreatedBy().getUsername());

        return finishTransaction(savedTransaction, entry);
    }

    private LoyaltyAccount getOrCreateAccount(Long customerId) {
        return loyaltyAccountRepository.findByCustomerId(customerId)
                .orElseGet(() -> loyaltyAccountRepository.save(LoyaltyAccount.builder().customer(resolveCustomer(customerId)).build()));
    }

    private LoyaltyAccount requireExistingAccount(Long customerId) {
        return loyaltyAccountRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new IllegalArgumentException("No loyalty account for customer: " + customerId));
    }

    private void requireSufficientBalance(LoyaltyAccount account, BigDecimal amount) {
        if (account.getAvailableBalance().compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient loyalty balance for customer " + account.getCustomer().getName()
                    + " - available " + account.getAvailableBalance() + ", requested " + amount);
        }
    }

    private LoyaltyTransaction buildTransaction(LoyaltyAccount account, LoyaltyTransactionType type, LoyaltyTransactionRequest request) {
        return LoyaltyTransaction.builder()
                .loyaltyAccount(account)
                .transactionType(type)
                .amount(request.getAmount())
                .balanceAfter(account.getAvailableBalance())
                .referenceType(request.getReferenceType())
                .referenceId(request.getReferenceId())
                .description(request.getDescription())
                .transactionDate(request.getTransactionDate())
                .createdBy(resolveUser(request.getCreatedByUserId()))
                .build();
    }

    private LoyaltyTransactionResponse finishTransaction(LoyaltyTransaction transaction, JournalEntry entry) {
        transaction.setJournalEntry(entry);
        LoyaltyTransaction saved = loyaltyTransactionRepository.save(transaction);
        log.info("Loyalty {} of {} for customer {} - GL entry #{}", saved.getTransactionType(), saved.getAmount(),
                saved.getLoyaltyAccount().getCustomer().getName(), entry.getEntryNumber());
        return toTransactionResponse(saved);
    }

    private Customers resolveCustomer(Long customerId) {
        return customersRepository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + customerId));
    }

    private UserAccount resolveUser(Long userId) {
        return userAccountRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
    }

    private LoyaltyAccountResponse toAccountResponse(LoyaltyAccount account) {
        return LoyaltyAccountResponse.builder()
                .id(account.getId())
                .customerId(account.getCustomer().getId())
                .customerName(account.getCustomer().getName())
                .availableBalance(account.getAvailableBalance())
                .totalEarned(account.getTotalEarned())
                .totalRedeemed(account.getTotalRedeemed())
                .totalExpired(account.getTotalExpired())
                .totalReversed(account.getTotalReversed())
                .build();
    }

    private LoyaltyTransactionResponse toTransactionResponse(LoyaltyTransaction transaction) {
        return LoyaltyTransactionResponse.builder()
                .id(transaction.getId())
                .customerId(transaction.getLoyaltyAccount().getCustomer().getId())
                .transactionType(transaction.getTransactionType().name())
                .amount(transaction.getAmount())
                .balanceAfter(transaction.getBalanceAfter())
                .referenceType(transaction.getReferenceType())
                .referenceId(transaction.getReferenceId())
                .description(transaction.getDescription())
                .transactionDate(transaction.getTransactionDate())
                .createdById(transaction.getCreatedBy() != null ? transaction.getCreatedBy().getId() : null)
                .createdByUsername(transaction.getCreatedBy() != null ? transaction.getCreatedBy().getUsername() : null)
                .createdAt(transaction.getCreatedAt())
                .journalEntryId(transaction.getJournalEntry() != null ? transaction.getJournalEntry().getId() : null)
                .journalEntryNumber(transaction.getJournalEntry() != null ? transaction.getJournalEntry().getEntryNumber() : null)
                .build();
    }
}
