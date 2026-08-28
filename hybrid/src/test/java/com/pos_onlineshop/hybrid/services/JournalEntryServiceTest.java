package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.account.Account;
import com.pos_onlineshop.hybrid.accountingPeriod.AccountingPeriod;
import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.dtos.JournalEntryDetailResponse;
import com.pos_onlineshop.hybrid.dtos.JournalEntryResponse;
import com.pos_onlineshop.hybrid.dtos.ReverseJournalEntryRequest;
import com.pos_onlineshop.hybrid.enums.AccountType;
import com.pos_onlineshop.hybrid.enums.DebitCredit;
import com.pos_onlineshop.hybrid.enums.GLSourceModule;
import com.pos_onlineshop.hybrid.enums.JournalStatus;
import com.pos_onlineshop.hybrid.enums.PeriodStatus;
import com.pos_onlineshop.hybrid.journalEntry.JournalEntry;
import com.pos_onlineshop.hybrid.journalEntry.JournalEntryRepository;
import com.pos_onlineshop.hybrid.journalLine.JournalLine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JournalEntryServiceTest {

    @Mock private JournalEntryRepository journalEntryRepository;
    @Mock private GLPostingService glPostingService;

    private JournalEntryService service;
    private Currency currency;
    private AccountingPeriod period;
    private Account cashAccount;
    private Account revenueAccount;

    @BeforeEach
    void setUp() {
        service = new JournalEntryService(journalEntryRepository, glPostingService);
        currency = Currency.builder().id(1L).code("USD").build();
        period = AccountingPeriod.builder().id(1L).name("2026-08")
                .startDate(LocalDate.of(2026, 8, 1)).endDate(LocalDate.of(2026, 8, 31))
                .status(PeriodStatus.OPEN).build();
        cashAccount = Account.builder().id(1L).code("1010").name("Cash").accountType(AccountType.ASSET)
                .normalBalance(DebitCredit.DEBIT).build();
        revenueAccount = Account.builder().id(2L).code("4000").name("Revenue").accountType(AccountType.REVENUE)
                .normalBalance(DebitCredit.CREDIT).build();
    }

    private JournalEntry postedEntry(Long id, Long entryNumber) {
        JournalEntry entry = JournalEntry.builder()
                .id(id).entryNumber(entryNumber).idempotencyKey("KEY-" + id)
                .entryDate(LocalDate.of(2026, 8, 15)).accountingPeriod(period)
                .description("Test entry").sourceModule(GLSourceModule.POS)
                .status(JournalStatus.POSTED).postedBy("system")
                .build();
        entry.addLine(JournalLine.builder().account(cashAccount)
                .debitAmount(new BigDecimal("100.00")).creditAmount(BigDecimal.ZERO)
                .currency(currency).baseAmount(new BigDecimal("100.00")).build());
        entry.addLine(JournalLine.builder().account(revenueAccount)
                .debitAmount(BigDecimal.ZERO).creditAmount(new BigDecimal("100.00"))
                .currency(currency).baseAmount(new BigDecimal("100.00")).build());
        return entry;
    }

    @Test
    void searchMapsEntriesToSummariesSortedByEntryNumberDescending() {
        JournalEntry first = postedEntry(1L, 1L);
        JournalEntry second = postedEntry(2L, 2L);
        when(journalEntryRepository.findAll(ArgumentMatchers.<Specification<JournalEntry>>any()))
                .thenReturn(List.of(first, second));

        List<JournalEntryResponse> results = service.search(null, null, null, null, null, null, null, null, null, null);

        assertEquals(2, results.size());
        assertEquals(2L, results.get(0).getEntryNumber());
        assertEquals(1L, results.get(1).getEntryNumber());
        assertEquals(0, new BigDecimal("100.00").compareTo(results.get(0).getTotalDebits()));
        assertEquals(0, new BigDecimal("100.00").compareTo(results.get(0).getTotalCredits()));
    }

    @Test
    void findByIdReturnsDetailWithLines() {
        JournalEntry entry = postedEntry(1L, 1L);
        when(journalEntryRepository.findById(1L)).thenReturn(Optional.of(entry));

        JournalEntryDetailResponse detail = service.findById(1L);

        assertEquals(2, detail.getLines().size());
        assertEquals("1010", detail.getLines().get(0).getAccountCode());
        assertEquals("4000", detail.getLines().get(1).getAccountCode());
    }

    @Test
    void findByIdThrowsWhenEntryDoesNotExist() {
        when(journalEntryRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.findById(99L));
    }

    @Test
    void reverseDefaultsReversalDateAndPostedByWhenNotSupplied() {
        JournalEntry original = postedEntry(1L, 1L);
        JournalEntry reversal = postedEntry(2L, 2L);
        when(journalEntryRepository.findById(1L)).thenReturn(Optional.of(original));
        when(glPostingService.reverse(eq(original), any(LocalDate.class), eq("Correction"), eq("system")))
                .thenReturn(reversal);

        ReverseJournalEntryRequest request = new ReverseJournalEntryRequest();
        request.setReason("Correction");

        JournalEntryDetailResponse response = service.reverse(1L, request);

        assertEquals(2L, response.getEntryNumber());
        verify(glPostingService).reverse(eq(original), eq(LocalDate.now()), eq("Correction"), eq("system"));
    }

    @Test
    void reverseUsesSuppliedReversalDateAndPostedBy() {
        JournalEntry original = postedEntry(1L, 1L);
        JournalEntry reversal = postedEntry(2L, 2L);
        when(journalEntryRepository.findById(1L)).thenReturn(Optional.of(original));
        LocalDate customDate = LocalDate.of(2026, 8, 20);
        when(glPostingService.reverse(original, customDate, "Voided", "alice")).thenReturn(reversal);

        ReverseJournalEntryRequest request = new ReverseJournalEntryRequest();
        request.setReason("Voided");
        request.setReversalDate(customDate);
        request.setPostedBy("alice");

        service.reverse(1L, request);

        verify(glPostingService).reverse(original, customDate, "Voided", "alice");
    }
}
