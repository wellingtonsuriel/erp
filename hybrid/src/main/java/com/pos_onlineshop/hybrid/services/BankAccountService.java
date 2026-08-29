package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.account.Account;
import com.pos_onlineshop.hybrid.account.AccountRepository;
import com.pos_onlineshop.hybrid.bankAccount.BankAccount;
import com.pos_onlineshop.hybrid.bankAccount.BankAccountRepository;
import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.currency.CurrencyRepository;
import com.pos_onlineshop.hybrid.dtos.BankAccountResponse;
import com.pos_onlineshop.hybrid.dtos.CreateBankAccountRequest;
import com.pos_onlineshop.hybrid.dtos.CreateOpeningBalanceRequest;
import com.pos_onlineshop.hybrid.dtos.OpeningBalanceLineRequest;
import com.pos_onlineshop.hybrid.enums.DebitCredit;
import com.pos_onlineshop.hybrid.shop.Shop;
import com.pos_onlineshop.hybrid.shop.ShopRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The subledger the flat 1010/1020/1030 control accounts never had - see BankAccount's
 * class comment. A positive opening balance is seeded through the existing
 * OpeningBalanceService rather than posting the GL entry here directly, so it gets exactly
 * the same automatic 3900 Opening Balance Equity plug every other opening balance does.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BankAccountService {

    private final BankAccountRepository bankAccountRepository;
    private final CurrencyRepository currencyRepository;
    private final ShopRepository shopRepository;
    private final AccountRepository accountRepository;
    private final OpeningBalanceService openingBalanceService;

    @Transactional(readOnly = true)
    public List<BankAccountResponse> findAllActive() {
        return bankAccountRepository.findByActiveTrue().stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public BankAccountResponse findById(Long id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    public BankAccountResponse create(CreateBankAccountRequest request) {
        if (bankAccountRepository.existsByAccountName(request.getAccountName())) {
            throw new IllegalArgumentException("A bank account named '" + request.getAccountName() + "' already exists");
        }
        Currency currency = currencyRepository.findById(request.getCurrencyId())
                .orElseThrow(() -> new IllegalArgumentException("Currency not found: " + request.getCurrencyId()));
        Shop shop = null;
        if (request.getShopId() != null) {
            shop = shopRepository.findById(request.getShopId())
                    .orElseThrow(() -> new IllegalArgumentException("Shop not found: " + request.getShopId()));
        }
        String glAccountCode = request.getGlAccountCode() != null
                ? request.getGlAccountCode() : BankAccount.defaultGlAccountCode(request.getAccountType());
        Account account = accountRepository.findByCode(glAccountCode)
                .orElseThrow(() -> new IllegalArgumentException("Account " + glAccountCode + " does not exist in the chart of accounts"));

        BigDecimal openingBalance = request.getOpeningBalance() != null ? request.getOpeningBalance() : BigDecimal.ZERO;
        if (openingBalance.compareTo(BigDecimal.ZERO) > 0 && request.getOpeningBalanceDate() == null) {
            throw new IllegalArgumentException("Opening balance date is required when an opening balance is provided");
        }

        BankAccount bankAccount = BankAccount.builder()
                .accountName(request.getAccountName())
                .accountNumber(request.getAccountNumber())
                .accountType(request.getAccountType())
                .glAccountCode(glAccountCode)
                .currency(currency)
                .shop(shop)
                .openingBalance(openingBalance)
                .currentBalance(openingBalance)
                .build();
        BankAccount saved = bankAccountRepository.save(bankAccount);

        if (openingBalance.compareTo(BigDecimal.ZERO) > 0) {
            postOpeningBalance(saved, account, currency, shop, openingBalance, request.getOpeningBalanceDate(), request.getCreatedByUserId());
        }

        log.info("Created bank account {} ({}) mapped to {}", saved.getAccountName(), saved.getAccountType(), glAccountCode);
        return toResponse(saved);
    }

    @Transactional
    public BankAccountResponse deactivate(Long id) {
        BankAccount bankAccount = findOrThrow(id);
        bankAccount.setActive(false);
        return toResponse(bankAccountRepository.save(bankAccount));
    }

    private void postOpeningBalance(BankAccount bankAccount, Account account, Currency currency, Shop shop,
                                     BigDecimal amount, LocalDate date, Long createdByUserId) {
        OpeningBalanceLineRequest line = new OpeningBalanceLineRequest();
        line.setAccountId(account.getId());
        line.setSide(DebitCredit.DEBIT);
        line.setAmount(amount);
        line.setCurrencyId(currency.getId());
        line.setCostCenterShopId(shop != null ? shop.getId() : null);
        line.setMemo("Opening balance for bank account " + bankAccount.getAccountName());

        CreateOpeningBalanceRequest request = new CreateOpeningBalanceRequest();
        request.setReference("BANK-ACCOUNT-OPEN-" + bankAccount.getId());
        request.setEntryDate(date);
        request.setDescription("Opening balance for bank account " + bankAccount.getAccountName());
        request.setCreatedByUserId(createdByUserId);
        request.setLines(List.of(line));

        openingBalanceService.createOpeningBalance(request);
    }

    private BankAccount findOrThrow(Long id) {
        return bankAccountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Bank account not found: " + id));
    }

    private BankAccountResponse toResponse(BankAccount bankAccount) {
        return BankAccountResponse.builder()
                .id(bankAccount.getId())
                .accountName(bankAccount.getAccountName())
                .accountNumber(bankAccount.getAccountNumber())
                .accountType(bankAccount.getAccountType().name())
                .glAccountCode(bankAccount.getGlAccountCode())
                .currencyCode(bankAccount.getCurrency() != null ? bankAccount.getCurrency().getCode() : null)
                .shopId(bankAccount.getShop() != null ? bankAccount.getShop().getId() : null)
                .shopName(bankAccount.getShop() != null ? bankAccount.getShop().getName() : null)
                .openingBalance(bankAccount.getOpeningBalance())
                .currentBalance(bankAccount.getCurrentBalance())
                .active(bankAccount.isActive())
                .createdAt(bankAccount.getCreatedAt())
                .build();
    }
}
