package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.account.Account;
import com.pos_onlineshop.hybrid.accountingPeriod.AccountingPeriod;
import com.pos_onlineshop.hybrid.accountingPeriod.AccountingPeriodRepository;
import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.enums.*;
import com.pos_onlineshop.hybrid.gl.ClosedPeriodException;
import com.pos_onlineshop.hybrid.gl.FinancialEvent;
import com.pos_onlineshop.hybrid.gl.PostingRuleNotFoundException;
import com.pos_onlineshop.hybrid.glNumbering.JournalNumberCounter;
import com.pos_onlineshop.hybrid.glNumbering.JournalNumberCounterRepository;
import com.pos_onlineshop.hybrid.journalEntry.JournalEntry;
import com.pos_onlineshop.hybrid.journalEntry.JournalEntryRepository;
import com.pos_onlineshop.hybrid.postingRule.PostingRule;
import com.pos_onlineshop.hybrid.postingRule.PostingRuleLine;
import com.pos_onlineshop.hybrid.postingRule.PostingRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * GLPostingService wired with mocked repositories + a real JournalNumberCounterRepository-backed
 * GLNumberingService (also mocked here, in-memory) and a real JournalValidator, so these tests
 * exercise the actual balance/period/control-account rules, not a stubbed-out validator.
 */
@ExtendWith(MockitoExtension.class)
class GLPostingServiceTest {

    @Mock private JournalEntryRepository journalEntryRepository;
    @Mock private PostingRuleRepository postingRuleRepository;
    @Mock private AccountingPeriodRepository accountingPeriodRepository;
    @Mock private JournalNumberCounterRepository counterRepository;

    private GLNumberingService numberingService;
    private GLPostingService glPostingService;

    private Account cash;
    private Account revenue;
    private Account vatPayable;
    private AccountingPeriod openPeriod;

    @BeforeEach
    void setUp() {
        numberingService = new GLNumberingService(counterRepository);
        glPostingService = new GLPostingService(
                journalEntryRepository, postingRuleRepository, accountingPeriodRepository,
                numberingService, new JournalValidator());

        cash = Account.builder().id(1L).code("1010").name("Cash on Hand")
                .accountType(AccountType.ASSET).normalBalance(DebitCredit.DEBIT).active(true).build();
        revenue = Account.builder().id(2L).code("4000").name("Sales Revenue")
                .accountType(AccountType.REVENUE).normalBalance(DebitCredit.CREDIT).active(true).build();
        vatPayable = Account.builder().id(3L).code("2200").name("VAT Payable")
                .accountType(AccountType.LIABILITY).normalBalance(DebitCredit.CREDIT).active(true).build();

        openPeriod = AccountingPeriod.builder().id(1L).name("2026-08")
                .startDate(LocalDate.of(2026, 8, 1)).endDate(LocalDate.of(2026, 8, 31))
                .status(PeriodStatus.OPEN).build();
    }

    private PostingRule cashSaleRule() {
        PostingRule rule = PostingRule.builder().id(1L).eventType(FinancialEventType.POS_CASH_SALE).active(true).build();
        rule.addLine(PostingRuleLine.builder().account(cash).side(DebitCredit.DEBIT).amountSource(AmountSource.GROSS).sequence(1).build());
        rule.addLine(PostingRuleLine.builder().account(revenue).side(DebitCredit.CREDIT).amountSource(AmountSource.NET).sequence(2).build());
        rule.addLine(PostingRuleLine.builder().account(vatPayable).side(DebitCredit.CREDIT).amountSource(AmountSource.TAX).sequence(3).build());
        return rule;
    }

    private FinancialEvent cashSaleEvent(String key) {
        return FinancialEvent.builder()
                .eventType(FinancialEventType.POS_CASH_SALE)
                .sourceModule(GLSourceModule.POS)
                .sourceReferenceType("ORDER")
                .sourceReferenceId(42L)
                .idempotencyKey(key)
                .eventDate(LocalDate.of(2026, 8, 15))
                .description("POS sale")
                .currency(Currency.builder().id(1L).code("USD").build())
                .exchangeRate(BigDecimal.ONE)
                .grossAmount(new BigDecimal("115.00"))
                .netAmount(new BigDecimal("100.00"))
                .taxAmount(new BigDecimal("15.00"))
                .postedBy("test-cashier")
                .build();
    }

    /** Simulates the counter row existing and being incrementable across calls. */
    private void stubCounter() {
        AtomicLong value = new AtomicLong(0);
        when(counterRepository.findByIdForUpdate(1L)).thenAnswer(inv -> {
            JournalNumberCounter c = JournalNumberCounter.builder().id(1L).lastValue(value.get()).build();
            return Optional.of(c);
        });
        when(counterRepository.save(any(JournalNumberCounter.class))).thenAnswer(inv -> {
            JournalNumberCounter c = inv.getArgument(0);
            value.set(c.getLastValue());
            return c;
        });
    }

    @Test
    void postsBalancedCashSaleEventThroughTheConfiguredRule() {
        stubCounter();
        when(journalEntryRepository.findByIdempotencyKey("POS-SALE-42")).thenReturn(Optional.empty());
        when(postingRuleRepository.findByEventTypeAndActiveTrue(FinancialEventType.POS_CASH_SALE))
                .thenReturn(Optional.of(cashSaleRule()));
        when(accountingPeriodRepository.findContaining(LocalDate.of(2026, 8, 15)))
                .thenReturn(Optional.of(openPeriod));
        when(journalEntryRepository.save(any(JournalEntry.class))).thenAnswer(inv -> inv.getArgument(0));

        JournalEntry result = glPostingService.post(cashSaleEvent("POS-SALE-42"));

        assertEquals(3, result.getLines().size());
        assertEquals(1L, result.getEntryNumber());
        assertEquals(JournalStatus.POSTED, result.getStatus());
        BigDecimal debits = result.getLines().stream().map(l -> l.getDebitAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal credits = result.getLines().stream().map(l -> l.getCreditAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, debits.compareTo(credits));
        assertEquals(0, new BigDecimal("115.00").compareTo(debits));
        verify(journalEntryRepository, times(1)).save(any(JournalEntry.class));
    }

    @Test
    void idempotentReplayReturnsExistingEntryWithoutPostingAgain() {
        JournalEntry existing = JournalEntry.builder().id(9L).entryNumber(5L).idempotencyKey("POS-SALE-42").build();
        when(journalEntryRepository.findByIdempotencyKey("POS-SALE-42")).thenReturn(Optional.of(existing));

        JournalEntry result = glPostingService.post(cashSaleEvent("POS-SALE-42"));

        assertSame(existing, result);
        verify(journalEntryRepository, never()).save(any());
        verifyNoInteractions(postingRuleRepository, accountingPeriodRepository, counterRepository);
    }

    @Test
    void postThrowsWhenNoPostingRuleIsConfigured() {
        when(journalEntryRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(postingRuleRepository.findByEventTypeAndActiveTrue(FinancialEventType.POS_CASH_SALE))
                .thenReturn(Optional.empty());

        assertThrows(PostingRuleNotFoundException.class, () -> glPostingService.post(cashSaleEvent("POS-SALE-1")));
    }

    @Test
    void postThrowsWhenNoPeriodCoversTheEventDate() {
        when(journalEntryRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(postingRuleRepository.findByEventTypeAndActiveTrue(FinancialEventType.POS_CASH_SALE))
                .thenReturn(Optional.of(cashSaleRule()));
        when(accountingPeriodRepository.findContaining(any())).thenReturn(Optional.empty());

        assertThrows(ClosedPeriodException.class, () -> glPostingService.post(cashSaleEvent("POS-SALE-1")));
    }

    @Test
    void postThrowsWhenPeriodIsClosed() {
        AccountingPeriod closed = AccountingPeriod.builder().id(2L).name("2026-08")
                .startDate(LocalDate.of(2026, 8, 1)).endDate(LocalDate.of(2026, 8, 31))
                .status(PeriodStatus.CLOSED).build();
        when(journalEntryRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(postingRuleRepository.findByEventTypeAndActiveTrue(FinancialEventType.POS_CASH_SALE))
                .thenReturn(Optional.of(cashSaleRule()));
        when(accountingPeriodRepository.findContaining(any())).thenReturn(Optional.of(closed));

        assertThrows(ClosedPeriodException.class, () -> glPostingService.post(cashSaleEvent("POS-SALE-1")));
    }

    @Test
    void reverseFlipsEachLineAndMarksOriginalReversed() {
        stubCounter();
        JournalEntry original = JournalEntry.builder()
                .id(1L).entryNumber(1L).idempotencyKey("POS-SALE-42")
                .entryDate(LocalDate.of(2026, 8, 15)).accountingPeriod(openPeriod)
                .sourceModule(GLSourceModule.POS).status(JournalStatus.POSTED).build();
        original.addLine(com.pos_onlineshop.hybrid.journalLine.JournalLine.builder()
                .account(cash).debitAmount(new BigDecimal("115.00")).creditAmount(BigDecimal.ZERO)
                .exchangeRate(BigDecimal.ONE).baseAmount(new BigDecimal("115.00")).build());
        original.addLine(com.pos_onlineshop.hybrid.journalLine.JournalLine.builder()
                .account(revenue).debitAmount(BigDecimal.ZERO).creditAmount(new BigDecimal("100.00"))
                .exchangeRate(BigDecimal.ONE).baseAmount(new BigDecimal("100.00")).build());
        original.addLine(com.pos_onlineshop.hybrid.journalLine.JournalLine.builder()
                .account(vatPayable).debitAmount(BigDecimal.ZERO).creditAmount(new BigDecimal("15.00"))
                .exchangeRate(BigDecimal.ONE).baseAmount(new BigDecimal("15.00")).build());

        when(journalEntryRepository.findByIdempotencyKey("REVERSAL-POS-SALE-42")).thenReturn(Optional.empty());
        when(accountingPeriodRepository.findContaining(LocalDate.of(2026, 8, 20))).thenReturn(Optional.of(openPeriod));
        when(journalEntryRepository.save(any(JournalEntry.class))).thenAnswer(inv -> inv.getArgument(0));

        JournalEntry reversal = glPostingService.reverse(original, LocalDate.of(2026, 8, 20), "customer return", "manager1");

        assertEquals(JournalStatus.REVERSED, original.getStatus());
        assertSame(reversal, original.getReversedByEntry());
        assertEquals(3, reversal.getLines().size());
        assertEquals(0, new BigDecimal("115.00").compareTo(
                reversal.getLines().stream().map(l -> l.getCreditAmount()).reduce(BigDecimal.ZERO, BigDecimal::add)));
        assertEquals(0, new BigDecimal("115.00").compareTo(
                reversal.getLines().stream().map(l -> l.getDebitAmount()).reduce(BigDecimal.ZERO, BigDecimal::add)));
        // save() called for the reversal entry and again to persist original's status flip
        verify(journalEntryRepository, times(2)).save(any(JournalEntry.class));
    }

    @Test
    void reversingAnAlreadyReversedEntryTwiceReturnsTheSameReversal() {
        JournalEntry original = JournalEntry.builder()
                .id(1L).entryNumber(1L).idempotencyKey("POS-SALE-42").status(JournalStatus.POSTED).build();
        JournalEntry existingReversal = JournalEntry.builder()
                .id(2L).entryNumber(2L).idempotencyKey("REVERSAL-POS-SALE-42").build();
        when(journalEntryRepository.findByIdempotencyKey("REVERSAL-POS-SALE-42"))
                .thenReturn(Optional.of(existingReversal));

        JournalEntry result = glPostingService.reverse(original, LocalDate.of(2026, 8, 20), "dup", "manager1");

        assertSame(existingReversal, result);
        verify(journalEntryRepository, never()).save(any());
    }
}
