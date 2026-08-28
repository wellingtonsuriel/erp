package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.dtos.JournalEntryDetailResponse;
import com.pos_onlineshop.hybrid.dtos.JournalEntryResponse;
import com.pos_onlineshop.hybrid.dtos.JournalLineResponse;
import com.pos_onlineshop.hybrid.dtos.ReverseJournalEntryRequest;
import com.pos_onlineshop.hybrid.enums.GLSourceModule;
import com.pos_onlineshop.hybrid.enums.JournalStatus;
import com.pos_onlineshop.hybrid.journalEntry.JournalEntry;
import com.pos_onlineshop.hybrid.journalEntry.JournalEntryRepository;
import com.pos_onlineshop.hybrid.journalEntry.JournalEntrySpecifications;
import com.pos_onlineshop.hybrid.journalLine.JournalLine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Read/browse and reverse for journal entries. There is deliberately no "create a raw
 * journal entry" operation here - a POSTED entry is only ever produced by
 * GLPostingService.post(FinancialEvent) from a real business event, or (once implemented)
 * by the manual-journal maker-checker workflow. Exposing a plain POST that lets a caller
 * hand-assemble JournalLines would bypass PostingRule/idempotency entirely.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class JournalEntryService {

    private final JournalEntryRepository journalEntryRepository;
    private final GLPostingService glPostingService;

    @Transactional(readOnly = true)
    public List<JournalEntryResponse> search(LocalDate fromDate, LocalDate toDate, Long periodId,
                                              GLSourceModule sourceModule, String sourceReferenceType,
                                              Long sourceReferenceId, Long accountId, Long shopId,
                                              JournalStatus status, Long entryNumber) {
        var spec = JournalEntrySpecifications.withFilters(fromDate, toDate, periodId, sourceModule,
                sourceReferenceType, sourceReferenceId, accountId, shopId, status, entryNumber);
        return journalEntryRepository.findAll(spec).stream()
                .sorted(Comparator.comparing(JournalEntry::getEntryNumber).reversed())
                .map(this::toSummary)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public JournalEntryDetailResponse findById(Long id) {
        return toDetail(findOrThrow(id));
    }

    public JournalEntryDetailResponse reverse(Long id, ReverseJournalEntryRequest request) {
        JournalEntry original = findOrThrow(id);
        LocalDate reversalDate = request.getReversalDate() != null ? request.getReversalDate() : LocalDate.now();
        String postedBy = request.getPostedBy() != null ? request.getPostedBy() : "system";
        JournalEntry reversal = glPostingService.reverse(original, reversalDate, request.getReason(), postedBy);
        return toDetail(reversal);
    }

    private JournalEntry findOrThrow(Long id) {
        return journalEntryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Journal entry not found: " + id));
    }

    private JournalEntryResponse toSummary(JournalEntry entry) {
        BigDecimal totalDebits = entry.getLines().stream().map(JournalLine::getDebitAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCredits = entry.getLines().stream().map(JournalLine::getCreditAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return JournalEntryResponse.builder()
                .id(entry.getId())
                .entryNumber(entry.getEntryNumber())
                .idempotencyKey(entry.getIdempotencyKey())
                .entryDate(entry.getEntryDate())
                .accountingPeriodName(entry.getAccountingPeriod().getName())
                .description(entry.getDescription())
                .sourceModule(entry.getSourceModule().name())
                .sourceReferenceType(entry.getSourceReferenceType())
                .sourceReferenceId(entry.getSourceReferenceId())
                .status(entry.getStatus().name())
                .postedBy(entry.getPostedBy())
                .postedAt(entry.getPostedAt())
                .totalDebits(totalDebits)
                .totalCredits(totalCredits)
                .reversalOfEntryId(entry.getReversalOfEntry() != null ? entry.getReversalOfEntry().getId() : null)
                .reversedByEntryId(entry.getReversedByEntry() != null ? entry.getReversedByEntry().getId() : null)
                .build();
    }

    private JournalEntryDetailResponse toDetail(JournalEntry entry) {
        List<JournalLineResponse> lines = entry.getLines().stream().map(line -> JournalLineResponse.builder()
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
                .build()).collect(Collectors.toList());

        return JournalEntryDetailResponse.builder()
                .id(entry.getId())
                .entryNumber(entry.getEntryNumber())
                .idempotencyKey(entry.getIdempotencyKey())
                .entryDate(entry.getEntryDate())
                .accountingPeriodName(entry.getAccountingPeriod().getName())
                .description(entry.getDescription())
                .sourceModule(entry.getSourceModule().name())
                .sourceReferenceType(entry.getSourceReferenceType())
                .sourceReferenceId(entry.getSourceReferenceId())
                .status(entry.getStatus().name())
                .postedBy(entry.getPostedBy())
                .postedAt(entry.getPostedAt())
                .reversalOfEntryId(entry.getReversalOfEntry() != null ? entry.getReversalOfEntry().getId() : null)
                .reversalOfEntryNumber(entry.getReversalOfEntry() != null ? entry.getReversalOfEntry().getEntryNumber() : null)
                .reversedByEntryId(entry.getReversedByEntry() != null ? entry.getReversedByEntry().getId() : null)
                .reversedByEntryNumber(entry.getReversedByEntry() != null ? entry.getReversedByEntry().getEntryNumber() : null)
                .lines(lines)
                .build();
    }
}
