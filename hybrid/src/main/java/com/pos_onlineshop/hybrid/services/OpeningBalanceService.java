package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.account.Account;
import com.pos_onlineshop.hybrid.account.AccountRepository;
import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.currency.CurrencyRepository;
import com.pos_onlineshop.hybrid.dtos.CreateOpeningBalanceRequest;
import com.pos_onlineshop.hybrid.dtos.JournalLineResponse;
import com.pos_onlineshop.hybrid.dtos.OpeningBalanceLineRequest;
import com.pos_onlineshop.hybrid.dtos.OpeningBalanceResponse;
import com.pos_onlineshop.hybrid.enums.DebitCredit;
import com.pos_onlineshop.hybrid.enums.GLSourceModule;
import com.pos_onlineshop.hybrid.gl.ManualLineSpec;
import com.pos_onlineshop.hybrid.journalEntry.JournalEntry;
import com.pos_onlineshop.hybrid.openingBalance.OpeningBalanceEntry;
import com.pos_onlineshop.hybrid.openingBalance.OpeningBalanceEntryRepository;
import com.pos_onlineshop.hybrid.shop.Shop;
import com.pos_onlineshop.hybrid.shop.ShopRepository;
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
 * Posts a one-time opening-balance journal (go-live conversion, or bringing an account
 * onto the books mid-year) straight through GLPostingService.postManual() with
 * sourceModule OPENING_BALANCE, rather than through ManualJournalService's maker-checker
 * pipeline. That detour is necessary, not incidental: JournalValidator blocks a
 * MANUAL-sourced line from touching a control account (1100 AR / 1200 Inventory / 2100
 * AP), and those are exactly the accounts an opening balance exists to seed. A dedicated
 * sourceModule lets this bypass that restriction for the one legitimate case it doesn't
 * apply to, without weakening the guard for actual manual journals.
 *
 * The caller supplies every line except the balancing entry against 3900 Opening Balance
 * Equity, which is computed here from the net of the supplied lines and appended
 * automatically - the preparer should never need to hand-compute a plug.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class OpeningBalanceService {

    private static final String OPENING_BALANCE_EQUITY_CODE = "3900";

    private final OpeningBalanceEntryRepository openingBalanceEntryRepository;
    private final AccountRepository accountRepository;
    private final CurrencyRepository currencyRepository;
    private final ShopRepository shopRepository;
    private final UserAccountRepository userAccountRepository;
    private final GLPostingService glPostingService;
    private final CurrencyService currencyService;

    public List<OpeningBalanceResponse> findAll() {
        return openingBalanceEntryRepository.findAllByOrderByIdDesc().stream()
                .map(this::toResponse).collect(Collectors.toList());
    }

    public OpeningBalanceResponse findById(Long id) {
        return toResponse(findOrThrow(id));
    }

    public OpeningBalanceResponse createOpeningBalance(CreateOpeningBalanceRequest request) {
        if (openingBalanceEntryRepository.existsByReference(request.getReference())) {
            throw new IllegalArgumentException("An opening balance with reference '"
                    + request.getReference() + "' already exists");
        }

        Account openingBalanceEquity = accountRepository.findByCode(OPENING_BALANCE_EQUITY_CODE)
                .orElseThrow(() -> new IllegalStateException(
                        "Opening Balance Equity account (" + OPENING_BALANCE_EQUITY_CODE + ") is not seeded"));
        Currency baseCurrency = currencyService.getBaseCurrency();
        UserAccount createdBy = resolveUser(request.getCreatedByUserId());

        BigDecimal netDebits = BigDecimal.ZERO;
        BigDecimal netCredits = BigDecimal.ZERO;
        List<ManualLineSpec> specs = new java.util.ArrayList<>();

        for (OpeningBalanceLineRequest lineRequest : request.getLines()) {
            Account account = accountRepository.findById(lineRequest.getAccountId())
                    .orElseThrow(() -> new IllegalArgumentException("Account not found: " + lineRequest.getAccountId()));
            if (account.getCode().equals(OPENING_BALANCE_EQUITY_CODE)) {
                throw new IllegalArgumentException(
                        "Do not supply a line against " + OPENING_BALANCE_EQUITY_CODE
                                + " (Opening Balance Equity) - the balancing line is computed automatically");
            }
            Currency currency = lineRequest.getCurrencyId() != null
                    ? currencyRepository.findById(lineRequest.getCurrencyId())
                            .orElseThrow(() -> new IllegalArgumentException("Currency not found: " + lineRequest.getCurrencyId()))
                    : baseCurrency;
            Shop shop = null;
            if (lineRequest.getCostCenterShopId() != null) {
                shop = shopRepository.findById(lineRequest.getCostCenterShopId())
                        .orElseThrow(() -> new IllegalArgumentException("Shop not found: " + lineRequest.getCostCenterShopId()));
            }
            BigDecimal exchangeRate = lineRequest.getExchangeRate() != null ? lineRequest.getExchangeRate() : BigDecimal.ONE;
            BigDecimal debit = lineRequest.getSide() == DebitCredit.DEBIT ? lineRequest.getAmount() : BigDecimal.ZERO;
            BigDecimal credit = lineRequest.getSide() == DebitCredit.CREDIT ? lineRequest.getAmount() : BigDecimal.ZERO;
            netDebits = netDebits.add(debit);
            netCredits = netCredits.add(credit);

            specs.add(new ManualLineSpec(account, debit, credit, currency, exchangeRate, shop, lineRequest.getMemo()));
        }

        BigDecimal difference = netDebits.subtract(netCredits);
        if (difference.compareTo(BigDecimal.ZERO) > 0) {
            // Explicit lines are net-debit, so the plug credits Opening Balance Equity.
            specs.add(new ManualLineSpec(openingBalanceEquity, BigDecimal.ZERO, difference, baseCurrency,
                    BigDecimal.ONE, null, "Opening balance equity"));
        } else if (difference.compareTo(BigDecimal.ZERO) < 0) {
            specs.add(new ManualLineSpec(openingBalanceEquity, difference.negate(), BigDecimal.ZERO, baseCurrency,
                    BigDecimal.ONE, null, "Opening balance equity"));
        }
        // If the explicit lines already balance (difference == 0), no plug line is needed.

        OpeningBalanceEntry header = OpeningBalanceEntry.builder()
                .reference(request.getReference())
                .entryDate(request.getEntryDate())
                .description(request.getDescription())
                .createdBy(createdBy)
                .build();
        header = openingBalanceEntryRepository.save(header);

        JournalEntry entry = glPostingService.postManual(
                "OPENING-BALANCE-" + request.getReference(),
                request.getEntryDate(),
                request.getDescription(),
                GLSourceModule.OPENING_BALANCE,
                "OPENING_BALANCE_ENTRY",
                header.getId(),
                specs,
                createdBy.getUsername());

        header.setPostedJournalEntry(entry);
        header = openingBalanceEntryRepository.save(header);

        log.info("Opening balance '{}' posted as GL entry #{}", request.getReference(), entry.getEntryNumber());
        return toResponse(header);
    }

    private UserAccount resolveUser(Long userId) {
        return userAccountRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
    }

    private OpeningBalanceEntry findOrThrow(Long id) {
        return openingBalanceEntryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Opening balance not found: " + id));
    }

    private OpeningBalanceResponse toResponse(OpeningBalanceEntry header) {
        List<JournalLineResponse> lines = header.getPostedJournalEntry().getLines().stream()
                .map(line -> JournalLineResponse.builder()
                        .id(line.getId())
                        .accountId(line.getAccount().getId())
                        .accountCode(line.getAccount().getCode())
                        .accountName(line.getAccount().getName())
                        .debitAmount(line.getDebitAmount())
                        .creditAmount(line.getCreditAmount())
                        .currencyCode(line.getCurrency() != null ? line.getCurrency().getCode() : null)
                        .baseAmount(line.getBaseAmount())
                        .exchangeRate(line.getExchangeRate())
                        .costCenterShopId(line.getCostCenterShop() != null ? line.getCostCenterShop().getId() : null)
                        .costCenterShopName(line.getCostCenterShop() != null ? line.getCostCenterShop().getName() : null)
                        .memo(line.getMemo())
                        .build())
                .collect(Collectors.toList());

        return OpeningBalanceResponse.builder()
                .id(header.getId())
                .reference(header.getReference())
                .entryDate(header.getEntryDate())
                .description(header.getDescription())
                .createdById(header.getCreatedBy() != null ? header.getCreatedBy().getId() : null)
                .createdByUsername(header.getCreatedBy() != null ? header.getCreatedBy().getUsername() : null)
                .createdAt(header.getCreatedAt())
                .postedJournalEntryId(header.getPostedJournalEntry().getId())
                .postedJournalEntryNumber(header.getPostedJournalEntry().getEntryNumber())
                .lines(lines)
                .build();
    }
}
