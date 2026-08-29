package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.account.Account;
import com.pos_onlineshop.hybrid.account.AccountRepository;
import com.pos_onlineshop.hybrid.accrual.AccrualEntry;
import com.pos_onlineshop.hybrid.accrual.AccrualEntryRepository;
import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.currency.CurrencyRepository;
import com.pos_onlineshop.hybrid.dtos.AccrualLineRequest;
import com.pos_onlineshop.hybrid.dtos.AccrualResponse;
import com.pos_onlineshop.hybrid.dtos.CreateAccrualRequest;
import com.pos_onlineshop.hybrid.dtos.JournalLineResponse;
import com.pos_onlineshop.hybrid.enums.AccrualStatus;
import com.pos_onlineshop.hybrid.enums.DebitCredit;
import com.pos_onlineshop.hybrid.enums.GLSourceModule;
import com.pos_onlineshop.hybrid.gl.ManualLineSpec;
import com.pos_onlineshop.hybrid.journalEntry.JournalEntry;
import com.pos_onlineshop.hybrid.shop.Shop;
import com.pos_onlineshop.hybrid.shop.ShopRepository;
import com.pos_onlineshop.hybrid.userAccount.UserAccount;
import com.pos_onlineshop.hybrid.userAccount.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Posts an accrual now via GLPostingService.postManual(sourceModule=ACCRUAL) and reverses
 * it later - explicitly via reverseAccrual(), or in bulk via reverseDueAccruals(), meant to
 * be called from period-close (or a scheduled job in a later stage) - using the existing
 * GLPostingService.reverse(), which already flips every line of a POSTED entry and is
 * idempotent by construction (its reversal key is derived from the original entry's
 * idempotency key). A dedicated sourceModule (rather than routing through
 * ManualJournalService) keeps accruals out of the maker-checker queue, since they are
 * typically a single preparer's period-end adjustment rather than an ongoing operational
 * entry - see OpeningBalanceService's class comment for the identical reasoning applied to
 * opening balances.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AccrualService {

    private final AccrualEntryRepository accrualEntryRepository;
    private final AccountRepository accountRepository;
    private final CurrencyRepository currencyRepository;
    private final ShopRepository shopRepository;
    private final UserAccountRepository userAccountRepository;
    private final GLPostingService glPostingService;
    private final CurrencyService currencyService;

    public List<AccrualResponse> findAll() {
        return accrualEntryRepository.findAllByOrderByIdDesc().stream()
                .map(this::toResponse).collect(Collectors.toList());
    }

    public AccrualResponse findById(Long id) {
        return toResponse(findOrThrow(id));
    }

    public AccrualResponse createAccrual(CreateAccrualRequest request) {
        if (accrualEntryRepository.existsByReference(request.getReference())) {
            throw new IllegalArgumentException("An accrual with reference '"
                    + request.getReference() + "' already exists");
        }
        if (request.getReversalDate().isBefore(request.getAccrualDate())) {
            throw new IllegalArgumentException("Reversal date cannot be before the accrual date");
        }

        Currency baseCurrency = currencyService.getBaseCurrency();
        UserAccount createdBy = resolveUser(request.getCreatedByUserId());

        List<ManualLineSpec> specs = request.getLines().stream()
                .map(lineRequest -> toLineSpec(lineRequest, baseCurrency))
                .collect(Collectors.toList());

        AccrualEntry header = AccrualEntry.builder()
                .reference(request.getReference())
                .accrualDate(request.getAccrualDate())
                .reversalDate(request.getReversalDate())
                .description(request.getDescription())
                .createdBy(createdBy)
                .build();
        header = accrualEntryRepository.save(header);

        JournalEntry entry = glPostingService.postManual(
                "ACCRUAL-" + request.getReference(),
                request.getAccrualDate(),
                request.getDescription(),
                GLSourceModule.ACCRUAL,
                "ACCRUAL_ENTRY",
                header.getId(),
                specs,
                createdBy.getUsername());

        header.setPostedJournalEntry(entry);
        header = accrualEntryRepository.save(header);

        log.info("Accrual '{}' posted as GL entry #{}, due for reversal on {}",
                request.getReference(), entry.getEntryNumber(), request.getReversalDate());
        return toResponse(header);
    }

    public AccrualResponse reverseAccrual(Long id, Long reversedByUserId) {
        AccrualEntry header = findOrThrow(id);
        if (header.getStatus() != AccrualStatus.PENDING_REVERSAL) {
            throw new IllegalStateException("Accrual " + id + " is already " + header.getStatus());
        }
        UserAccount reversedBy = resolveUser(reversedByUserId);

        JournalEntry reversal = glPostingService.reverse(
                header.getPostedJournalEntry(), header.getReversalDate(), "Accrual reversal", reversedBy.getUsername());

        header.setStatus(AccrualStatus.REVERSED);
        header.setReversalJournalEntry(reversal);
        header.setReversedAt(LocalDateTime.now());
        header = accrualEntryRepository.save(header);

        log.info("Accrual '{}' reversed as GL entry #{}", header.getReference(), reversal.getEntryNumber());
        return toResponse(header);
    }

    /** Reverses every PENDING_REVERSAL accrual whose reversalDate has arrived, using the
     * SYSTEM user identity - meant to be driven by period-close or a scheduled job, not a
     * human clicking through one accrual at a time. Returns the accruals it reversed. */
    public List<AccrualResponse> reverseDueAccruals(LocalDate asOfDate) {
        List<AccrualEntry> due = accrualEntryRepository
                .findByStatusAndReversalDateLessThanEqual(AccrualStatus.PENDING_REVERSAL, asOfDate);

        return due.stream().map(header -> {
            JournalEntry reversal = glPostingService.reverse(
                    header.getPostedJournalEntry(), header.getReversalDate(), "Accrual reversal (due)", "SYSTEM");
            header.setStatus(AccrualStatus.REVERSED);
            header.setReversalJournalEntry(reversal);
            header.setReversedAt(LocalDateTime.now());
            AccrualEntry saved = accrualEntryRepository.save(header);
            log.info("Accrual '{}' auto-reversed as GL entry #{} (due as of {})",
                    saved.getReference(), reversal.getEntryNumber(), asOfDate);
            return toResponse(saved);
        }).collect(Collectors.toList());
    }

    private ManualLineSpec toLineSpec(AccrualLineRequest lineRequest, Currency baseCurrency) {
        Account account = accountRepository.findById(lineRequest.getAccountId())
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + lineRequest.getAccountId()));
        Currency currency = lineRequest.getCurrencyId() != null
                ? currencyRepository.findById(lineRequest.getCurrencyId())
                        .orElseThrow(() -> new IllegalArgumentException("Currency not found: " + lineRequest.getCurrencyId()))
                : baseCurrency;
        Shop shop = null;
        if (lineRequest.getCostCenterShopId() != null) {
            shop = shopRepository.findById(lineRequest.getCostCenterShopId())
                    .orElseThrow(() -> new IllegalArgumentException("Shop not found: " + lineRequest.getCostCenterShopId()));
        }
        java.math.BigDecimal debit = lineRequest.getSide() == DebitCredit.DEBIT ? lineRequest.getAmount() : java.math.BigDecimal.ZERO;
        java.math.BigDecimal credit = lineRequest.getSide() == DebitCredit.CREDIT ? lineRequest.getAmount() : java.math.BigDecimal.ZERO;
        return new ManualLineSpec(account, debit, credit, currency,
                lineRequest.getExchangeRate() != null ? lineRequest.getExchangeRate() : java.math.BigDecimal.ONE,
                shop, lineRequest.getMemo());
    }

    private UserAccount resolveUser(Long userId) {
        return userAccountRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
    }

    private AccrualEntry findOrThrow(Long id) {
        return accrualEntryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Accrual not found: " + id));
    }

    private AccrualResponse toResponse(AccrualEntry header) {
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

        return AccrualResponse.builder()
                .id(header.getId())
                .reference(header.getReference())
                .accrualDate(header.getAccrualDate())
                .reversalDate(header.getReversalDate())
                .description(header.getDescription())
                .status(header.getStatus().name())
                .createdById(header.getCreatedBy() != null ? header.getCreatedBy().getId() : null)
                .createdByUsername(header.getCreatedBy() != null ? header.getCreatedBy().getUsername() : null)
                .createdAt(header.getCreatedAt())
                .postedJournalEntryId(header.getPostedJournalEntry().getId())
                .postedJournalEntryNumber(header.getPostedJournalEntry().getEntryNumber())
                .reversalJournalEntryId(header.getReversalJournalEntry() != null ? header.getReversalJournalEntry().getId() : null)
                .reversalJournalEntryNumber(header.getReversalJournalEntry() != null ? header.getReversalJournalEntry().getEntryNumber() : null)
                .reversedAt(header.getReversedAt())
                .lines(lines)
                .build();
    }
}
