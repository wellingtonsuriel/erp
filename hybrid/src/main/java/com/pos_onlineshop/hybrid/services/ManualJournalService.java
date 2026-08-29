package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.account.Account;
import com.pos_onlineshop.hybrid.account.AccountRepository;
import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.currency.CurrencyRepository;
import com.pos_onlineshop.hybrid.dtos.*;
import com.pos_onlineshop.hybrid.enums.DebitCredit;
import com.pos_onlineshop.hybrid.enums.GLSourceModule;
import com.pos_onlineshop.hybrid.gl.ManualLineSpec;
import com.pos_onlineshop.hybrid.journalEntry.JournalEntry;
import com.pos_onlineshop.hybrid.manualJournal.ManualJournal;
import com.pos_onlineshop.hybrid.manualJournal.ManualJournalRepository;
import com.pos_onlineshop.hybrid.manualJournalLine.ManualJournalLine;
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
 * Maker-checker workflow for manual journals: DRAFT -> SUBMITTED -> APPROVED -> POSTED,
 * or SUBMITTED -> REJECTED. The preparer (createdBy/submittedBy) may never also be the
 * approver - enforced in ManualJournal.approve(). Posting is a separate explicit action
 * from approval so an APPROVED journal can wait for e.g. its accounting period to open
 * without blocking the approval step itself.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ManualJournalService {

    private final ManualJournalRepository manualJournalRepository;
    private final AccountRepository accountRepository;
    private final CurrencyRepository currencyRepository;
    private final ShopRepository shopRepository;
    private final UserAccountRepository userAccountRepository;
    private final GLPostingService glPostingService;
    private final CurrencyService currencyService;

    public List<ManualJournalResponse> findAll() {
        return manualJournalRepository.findAllByOrderByIdDesc().stream()
                .map(this::toResponse).collect(Collectors.toList());
    }

    public ManualJournalResponse findById(Long id) {
        return toResponse(findOrThrow(id));
    }

    public ManualJournalResponse create(CreateManualJournalRequest request) {
        UserAccount createdBy = resolveUser(request.getCreatedByUserId());
        Currency baseCurrency = currencyService.getBaseCurrency();

        ManualJournal journal = ManualJournal.builder()
                .entryDate(request.getEntryDate())
                .description(request.getDescription())
                .createdBy(createdBy)
                .attachmentReference(request.getAttachmentReference())
                .build();

        for (ManualJournalLineRequest lineRequest : request.getLines()) {
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

            ManualJournalLine line = ManualJournalLine.builder()
                    .account(account)
                    .debitAmount(lineRequest.getSide() == DebitCredit.DEBIT ? lineRequest.getAmount() : BigDecimal.ZERO)
                    .creditAmount(lineRequest.getSide() == DebitCredit.CREDIT ? lineRequest.getAmount() : BigDecimal.ZERO)
                    .currency(currency)
                    .exchangeRate(lineRequest.getExchangeRate() != null ? lineRequest.getExchangeRate() : BigDecimal.ONE)
                    .costCenterShop(shop)
                    .memo(lineRequest.getMemo())
                    .build();
            journal.addLine(line);
        }

        // Fails fast on an unbalanced draft rather than waiting until submit().
        journal.validateBalance();

        return toResponse(manualJournalRepository.save(journal));
    }

    public ManualJournalResponse submit(Long id, ManualJournalActionRequest request) {
        ManualJournal journal = findOrThrow(id);
        UserAccount submittedBy = resolveUser(request.getUserId());
        journal.submit(submittedBy);
        return toResponse(manualJournalRepository.save(journal));
    }

    public ManualJournalResponse approve(Long id, ManualJournalActionRequest request) {
        ManualJournal journal = findOrThrow(id);
        UserAccount approvedBy = resolveUser(request.getUserId());
        journal.approve(approvedBy);
        return toResponse(manualJournalRepository.save(journal));
    }

    public ManualJournalResponse reject(Long id, RejectManualJournalRequest request) {
        ManualJournal journal = findOrThrow(id);
        UserAccount rejectedBy = resolveUser(request.getUserId());
        journal.reject(rejectedBy, request.getReason());
        return toResponse(manualJournalRepository.save(journal));
    }

    public ManualJournalResponse post(Long id, ManualJournalActionRequest request) {
        ManualJournal journal = findOrThrow(id);
        if (!journal.canBePosted()) {
            throw new IllegalStateException("Manual journal " + id + " cannot be posted from status " + journal.getStatus());
        }
        UserAccount postedByUser = resolveUser(request.getUserId());

        List<ManualLineSpec> specs = journal.getLines().stream()
                .map(line -> new ManualLineSpec(line.getAccount(), line.getDebitAmount(), line.getCreditAmount(),
                        line.getCurrency(), line.getExchangeRate(), line.getCostCenterShop(), line.getMemo()))
                .collect(Collectors.toList());

        JournalEntry entry = glPostingService.postManual(
                "MANUAL-JOURNAL-" + journal.getId(),
                journal.getEntryDate(),
                journal.getDescription(),
                GLSourceModule.MANUAL,
                "MANUAL_JOURNAL",
                journal.getId(),
                specs,
                postedByUser.getUsername());

        journal.markPosted(entry);
        log.info("Manual journal {} posted as GL entry #{}", journal.getId(), entry.getEntryNumber());
        return toResponse(manualJournalRepository.save(journal));
    }

    private UserAccount resolveUser(Long userId) {
        return userAccountRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
    }

    private ManualJournal findOrThrow(Long id) {
        return manualJournalRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Manual journal not found: " + id));
    }

    private ManualJournalResponse toResponse(ManualJournal journal) {
        List<ManualJournalLineResponse> lines = journal.getLines().stream().map(line -> {
            boolean isDebit = line.getDebitAmount() != null && line.getDebitAmount().compareTo(BigDecimal.ZERO) > 0;
            return ManualJournalLineResponse.builder()
                    .id(line.getId())
                    .accountId(line.getAccount().getId())
                    .accountCode(line.getAccount().getCode())
                    .accountName(line.getAccount().getName())
                    .side(isDebit ? DebitCredit.DEBIT : DebitCredit.CREDIT)
                    .amount(isDebit ? line.getDebitAmount() : line.getCreditAmount())
                    .currencyCode(line.getCurrency() != null ? line.getCurrency().getCode() : null)
                    .costCenterShopId(line.getCostCenterShop() != null ? line.getCostCenterShop().getId() : null)
                    .costCenterShopName(line.getCostCenterShop() != null ? line.getCostCenterShop().getName() : null)
                    .memo(line.getMemo())
                    .build();
        }).collect(Collectors.toList());

        return ManualJournalResponse.builder()
                .id(journal.getId())
                .reference("MJ-" + String.format("%06d", journal.getId() != null ? journal.getId() : 0))
                .status(journal.getStatus().name())
                .entryDate(journal.getEntryDate())
                .description(journal.getDescription())
                .attachmentReference(journal.getAttachmentReference())
                .createdById(journal.getCreatedBy() != null ? journal.getCreatedBy().getId() : null)
                .createdByUsername(journal.getCreatedBy() != null ? journal.getCreatedBy().getUsername() : null)
                .createdAt(journal.getCreatedAt())
                .submittedById(journal.getSubmittedBy() != null ? journal.getSubmittedBy().getId() : null)
                .submittedByUsername(journal.getSubmittedBy() != null ? journal.getSubmittedBy().getUsername() : null)
                .submittedAt(journal.getSubmittedAt())
                .approvedById(journal.getApprovedBy() != null ? journal.getApprovedBy().getId() : null)
                .approvedByUsername(journal.getApprovedBy() != null ? journal.getApprovedBy().getUsername() : null)
                .approvedAt(journal.getApprovedAt())
                .rejectedById(journal.getRejectedBy() != null ? journal.getRejectedBy().getId() : null)
                .rejectedByUsername(journal.getRejectedBy() != null ? journal.getRejectedBy().getUsername() : null)
                .rejectedAt(journal.getRejectedAt())
                .rejectionReason(journal.getRejectionReason())
                .postedJournalEntryId(journal.getPostedJournalEntry() != null ? journal.getPostedJournalEntry().getId() : null)
                .postedJournalEntryNumber(journal.getPostedJournalEntry() != null ? journal.getPostedJournalEntry().getEntryNumber() : null)
                .lines(lines)
                .build();
    }
}
