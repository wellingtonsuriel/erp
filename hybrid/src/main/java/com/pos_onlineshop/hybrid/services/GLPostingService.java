package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.accountingPeriod.AccountingPeriod;
import com.pos_onlineshop.hybrid.accountingPeriod.AccountingPeriodRepository;
import com.pos_onlineshop.hybrid.enums.AmountSource;
import com.pos_onlineshop.hybrid.enums.DebitCredit;
import com.pos_onlineshop.hybrid.enums.JournalStatus;
import com.pos_onlineshop.hybrid.enums.GLSourceModule;
import com.pos_onlineshop.hybrid.gl.ClosedPeriodException;
import com.pos_onlineshop.hybrid.gl.FinancialEvent;
import com.pos_onlineshop.hybrid.gl.GLPostingException;
import com.pos_onlineshop.hybrid.gl.ManualLineSpec;
import com.pos_onlineshop.hybrid.gl.PostingRuleNotFoundException;
import com.pos_onlineshop.hybrid.journalEntry.JournalEntry;
import com.pos_onlineshop.hybrid.journalEntry.JournalEntryRepository;
import com.pos_onlineshop.hybrid.journalLine.JournalLine;
import com.pos_onlineshop.hybrid.postingRule.PostingRule;
import com.pos_onlineshop.hybrid.postingRule.PostingRuleLine;
import com.pos_onlineshop.hybrid.postingRule.PostingRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The only service in the codebase allowed to construct a POSTED JournalEntry.
 * Business services (POSService, and in future OrderService/InventoryService/...) never
 * build JournalLines themselves - they build a FinancialEvent and call post().
 *
 * post() must be called from inside the same @Transactional boundary as the business
 * event it represents (see POSService.processQuickSale): if that transaction rolls back,
 * the journal entry and the number it consumed roll back with it. There is deliberately
 * no async/queued posting here - see the GL report's Migration section for why.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GLPostingService {

    private final JournalEntryRepository journalEntryRepository;
    private final PostingRuleRepository postingRuleRepository;
    private final AccountingPeriodRepository accountingPeriodRepository;
    private final GLNumberingService numberingService;
    private final JournalValidator validator;

    @Transactional
    public JournalEntry post(FinancialEvent event) {
        Optional<JournalEntry> existing = journalEntryRepository.findByIdempotencyKey(event.getIdempotencyKey());
        if (existing.isPresent()) {
            log.info("GL: idempotent replay for key {} - returning existing entry #{}",
                    event.getIdempotencyKey(), existing.get().getEntryNumber());
            return existing.get();
        }

        PostingRule rule = postingRuleRepository.findByEventTypeAndActiveTrue(event.getEventType())
                .orElseThrow(() -> new PostingRuleNotFoundException(
                        "No active posting rule for event type: " + event.getEventType()));

        AccountingPeriod period = resolvePeriod(event.getEventDate());

        JournalEntry entry = JournalEntry.builder()
                .entryNumber(numberingService.nextEntryNumber())
                .idempotencyKey(event.getIdempotencyKey())
                .entryDate(event.getEventDate())
                .accountingPeriod(period)
                .description(event.getDescription())
                .sourceModule(event.getSourceModule())
                .sourceReferenceType(event.getSourceReferenceType())
                .sourceReferenceId(event.getSourceReferenceId())
                .status(JournalStatus.POSTED)
                .postedBy(event.getPostedBy())
                .postedAt(LocalDateTime.now())
                .build();

        List<PostingRuleLine> ruleLines = rule.getLines().stream()
                .sorted(Comparator.comparingInt(PostingRuleLine::getSequence))
                .toList();

        BigDecimal exchangeRate = event.getExchangeRate() != null ? event.getExchangeRate() : BigDecimal.ONE;

        for (PostingRuleLine ruleLine : ruleLines) {
            BigDecimal amount = resolveAmount(event, ruleLine.getAmountSource());
            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                // e.g. no COST supplied on this event -> its COGS/Inventory pair is skipped
                continue;
            }
            JournalLine line = JournalLine.builder()
                    .account(ruleLine.getAccount())
                    .debitAmount(ruleLine.getSide() == DebitCredit.DEBIT ? amount : BigDecimal.ZERO)
                    .creditAmount(ruleLine.getSide() == DebitCredit.CREDIT ? amount : BigDecimal.ZERO)
                    .currency(event.getCurrency())
                    .baseAmount(amount.multiply(exchangeRate).setScale(4, RoundingMode.HALF_UP))
                    .exchangeRate(exchangeRate)
                    .costCenterShop(ruleLine.getShopRole() == com.pos_onlineshop.hybrid.enums.ShopRole.DESTINATION
                            ? event.getDestinationShop() : event.getShop())
                    .memo(event.getDescription())
                    .build();
            entry.addLine(line);
        }

        validator.validate(entry);

        try {
            JournalEntry saved = journalEntryRepository.save(entry);
            log.info("GL: posted entry #{} ({}) for {}", saved.getEntryNumber(), event.getEventType(), event.getIdempotencyKey());
            return saved;
        } catch (DataIntegrityViolationException e) {
            // Lost a race on the idempotencyKey unique constraint to a concurrent identical post.
            return journalEntryRepository.findByIdempotencyKey(event.getIdempotencyKey())
                    .orElseThrow(() -> e);
        }
    }

    /**
     * Reverses a POSTED entry line-by-line (each debit becomes a credit of the same amount
     * and vice versa), rather than re-deriving a generic refund pair. Idempotent: reversing
     * the same entry twice returns the first reversal.
     */
    @Transactional
    public JournalEntry reverse(JournalEntry original, LocalDate reversalDate, String reason, String postedBy) {
        if (original.getStatus() != JournalStatus.POSTED) {
            throw new GLPostingException(
                    "Only a POSTED entry can be reversed (entry #" + original.getEntryNumber()
                            + " is " + original.getStatus() + ")");
        }

        String reversalKey = "REVERSAL-" + original.getIdempotencyKey();
        Optional<JournalEntry> existingReversal = journalEntryRepository.findByIdempotencyKey(reversalKey);
        if (existingReversal.isPresent()) {
            return existingReversal.get();
        }

        AccountingPeriod period = resolvePeriod(reversalDate);

        JournalEntry reversal = JournalEntry.builder()
                .entryNumber(numberingService.nextEntryNumber())
                .idempotencyKey(reversalKey)
                .entryDate(reversalDate)
                .accountingPeriod(period)
                .description("Reversal (" + reason + ") of entry #" + original.getEntryNumber())
                .sourceModule(original.getSourceModule())
                .sourceReferenceType(original.getSourceReferenceType())
                .sourceReferenceId(original.getSourceReferenceId())
                .status(JournalStatus.POSTED)
                .postedBy(postedBy)
                .postedAt(LocalDateTime.now())
                .reversalOfEntry(original)
                .build();

        for (JournalLine source : original.getLines()) {
            JournalLine flipped = JournalLine.builder()
                    .account(source.getAccount())
                    .debitAmount(source.getCreditAmount())
                    .creditAmount(source.getDebitAmount())
                    .currency(source.getCurrency())
                    .baseAmount(source.getBaseAmount())
                    .exchangeRate(source.getExchangeRate())
                    .costCenterShop(source.getCostCenterShop())
                    .memo("Reversal of entry #" + original.getEntryNumber())
                    .build();
            reversal.addLine(flipped);
        }

        validator.validate(reversal);

        JournalEntry savedReversal;
        try {
            savedReversal = journalEntryRepository.save(reversal);
        } catch (DataIntegrityViolationException e) {
            return journalEntryRepository.findByIdempotencyKey(reversalKey).orElseThrow(() -> e);
        }

        original.setStatus(JournalStatus.REVERSED);
        original.setReversedByEntry(savedReversal);
        journalEntryRepository.save(original);

        log.info("GL: reversed entry #{} with entry #{} ({})",
                original.getEntryNumber(), savedReversal.getEntryNumber(), reason);
        return savedReversal;
    }

    /**
     * Posts a fully-specified set of lines (accounts and debit/credit amounts already
     * chosen by the caller) rather than resolving them from a PostingRule. Used by
     * ManualJournalService (sourceModule MANUAL) once a manual journal is APPROVED - see
     * ManualJournal's class comment for why manual journals bypass PostingRule entirely -
     * and by AccountingPeriodService's period-close revenue/expense sweep (sourceModule
     * SYSTEM, since that entry is generated by a routine, not typed by a human). Whichever
     * sourceModule is passed, JournalValidator's control-account rule still applies exactly
     * as it would to any other entry from that module.
     */
    @Transactional
    public JournalEntry postManual(String idempotencyKey, LocalDate entryDate, String description,
                                    GLSourceModule sourceModule, String sourceReferenceType, Long sourceReferenceId,
                                    List<ManualLineSpec> lineSpecs, String postedBy) {
        Optional<JournalEntry> existing = journalEntryRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.info("GL: idempotent replay for key {} - returning existing entry #{}",
                    idempotencyKey, existing.get().getEntryNumber());
            return existing.get();
        }

        AccountingPeriod period = resolvePeriod(entryDate);

        JournalEntry entry = JournalEntry.builder()
                .entryNumber(numberingService.nextEntryNumber())
                .idempotencyKey(idempotencyKey)
                .entryDate(entryDate)
                .accountingPeriod(period)
                .description(description)
                .sourceModule(sourceModule)
                .sourceReferenceType(sourceReferenceType)
                .sourceReferenceId(sourceReferenceId)
                .status(JournalStatus.POSTED)
                .postedBy(postedBy)
                .postedAt(LocalDateTime.now())
                .build();

        for (ManualLineSpec spec : lineSpecs) {
            BigDecimal exchangeRate = spec.exchangeRate() != null ? spec.exchangeRate() : BigDecimal.ONE;
            BigDecimal debit = spec.debitAmount() != null ? spec.debitAmount() : BigDecimal.ZERO;
            BigDecimal credit = spec.creditAmount() != null ? spec.creditAmount() : BigDecimal.ZERO;
            BigDecimal amount = debit.compareTo(BigDecimal.ZERO) > 0 ? debit : credit;
            JournalLine line = JournalLine.builder()
                    .account(spec.account())
                    .debitAmount(debit)
                    .creditAmount(credit)
                    .currency(spec.currency())
                    .baseAmount(amount.multiply(exchangeRate).setScale(4, RoundingMode.HALF_UP))
                    .exchangeRate(exchangeRate)
                    .costCenterShop(spec.costCenterShop())
                    .memo(spec.memo())
                    .build();
            entry.addLine(line);
        }

        validator.validate(entry);

        try {
            JournalEntry saved = journalEntryRepository.save(entry);
            log.info("GL: posted manual entry #{} for {}", saved.getEntryNumber(), idempotencyKey);
            return saved;
        } catch (DataIntegrityViolationException e) {
            return journalEntryRepository.findByIdempotencyKey(idempotencyKey).orElseThrow(() -> e);
        }
    }

    private AccountingPeriod resolvePeriod(LocalDate date) {
        AccountingPeriod period = accountingPeriodRepository.findContaining(date)
                .orElseThrow(() -> new ClosedPeriodException("No accounting period covers date " + date));
        if (!period.acceptsPosting()) {
            throw new ClosedPeriodException(
                    "Accounting period " + period.getName() + " is " + period.getStatus() + " - cannot post");
        }
        return period;
    }

    private BigDecimal resolveAmount(FinancialEvent event, AmountSource source) {
        return switch (source) {
            case GROSS -> event.getGrossAmount();
            case NET -> event.getNetAmount();
            case TAX -> event.getTaxAmount();
            case COST -> event.getCostAmount();
        };
    }
}
