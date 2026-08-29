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
import com.pos_onlineshop.hybrid.enums.AccountType;
import com.pos_onlineshop.hybrid.enums.AccrualStatus;
import com.pos_onlineshop.hybrid.enums.DebitCredit;
import com.pos_onlineshop.hybrid.enums.GLSourceModule;
import com.pos_onlineshop.hybrid.gl.ManualLineSpec;
import com.pos_onlineshop.hybrid.journalEntry.JournalEntry;
import com.pos_onlineshop.hybrid.journalLine.JournalLine;
import com.pos_onlineshop.hybrid.shop.ShopRepository;
import com.pos_onlineshop.hybrid.userAccount.UserAccount;
import com.pos_onlineshop.hybrid.userAccount.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccrualServiceTest {

    @Mock private AccrualEntryRepository accrualEntryRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private CurrencyRepository currencyRepository;
    @Mock private ShopRepository shopRepository;
    @Mock private UserAccountRepository userAccountRepository;
    @Mock private GLPostingService glPostingService;
    @Mock private CurrencyService currencyService;

    private AccrualService service;

    private UserAccount user;
    private Currency baseCurrency;
    private Account accruedExpense;
    private Account expense;

    @BeforeEach
    void setUp() {
        service = new AccrualService(accrualEntryRepository, accountRepository, currencyRepository,
                shopRepository, userAccountRepository, glPostingService, currencyService);

        user = UserAccount.builder().id(1L).username("controller1").password("x").email("c@test.com").build();
        baseCurrency = Currency.builder().id(1L).code("USD").build();
        accruedExpense = Account.builder().id(10L).code("2200").name("Accrued Expenses")
                .accountType(AccountType.LIABILITY).normalBalance(DebitCredit.CREDIT).controlAccount(false).active(true).build();
        expense = Account.builder().id(11L).code("5000").name("Rent Expense")
                .accountType(AccountType.EXPENSE).normalBalance(DebitCredit.DEBIT).controlAccount(false).active(true).build();

        lenient().when(currencyService.getBaseCurrency()).thenReturn(baseCurrency);
        lenient().when(userAccountRepository.findById(1L)).thenReturn(Optional.of(user));
    }

    private CreateAccrualRequest balancedRequest() {
        AccrualLineRequest debit = new AccrualLineRequest();
        debit.setAccountId(11L);
        debit.setSide(DebitCredit.DEBIT);
        debit.setAmount(new BigDecimal("1000.00"));
        AccrualLineRequest credit = new AccrualLineRequest();
        credit.setAccountId(10L);
        credit.setSide(DebitCredit.CREDIT);
        credit.setAmount(new BigDecimal("1000.00"));

        CreateAccrualRequest request = new CreateAccrualRequest();
        request.setReference("ACR-2026-001");
        request.setAccrualDate(LocalDate.of(2026, 8, 31));
        request.setReversalDate(LocalDate.of(2026, 9, 1));
        request.setDescription("Accrue August rent not yet invoiced");
        request.setCreatedByUserId(1L);
        request.setLines(List.of(debit, credit));
        return request;
    }

    private JournalEntry postedEntryWithLines(List<ManualLineSpec> specs, long entryNumber) {
        JournalEntry entry = JournalEntry.builder().id(entryNumber).entryNumber(entryNumber).build();
        for (ManualLineSpec spec : specs) {
            entry.addLine(JournalLine.builder().id((long) entry.getLines().size() + 1)
                    .account(spec.account()).debitAmount(spec.debitAmount()).creditAmount(spec.creditAmount())
                    .currency(spec.currency()).baseAmount(spec.debitAmount().max(spec.creditAmount()))
                    .exchangeRate(spec.exchangeRate()).costCenterShop(spec.costCenterShop()).memo(spec.memo()).build());
        }
        return entry;
    }

    @Test
    void createAccrualPostsExactlyTheSuppliedLinesWithNoAutomaticPlug() {
        when(accrualEntryRepository.existsByReference("ACR-2026-001")).thenReturn(false);
        when(accountRepository.findById(11L)).thenReturn(Optional.of(expense));
        when(accountRepository.findById(10L)).thenReturn(Optional.of(accruedExpense));
        when(accrualEntryRepository.save(any(AccrualEntry.class))).thenAnswer(inv -> {
            AccrualEntry entry = inv.getArgument(0);
            if (entry.getId() == null) entry.setId(20L);
            return entry;
        });

        ArgumentCaptor<List<ManualLineSpec>> specsCaptor = ArgumentCaptor.forClass(List.class);
        when(glPostingService.postManual(eq("ACCRUAL-ACR-2026-001"), eq(LocalDate.of(2026, 8, 31)), anyString(),
                eq(GLSourceModule.ACCRUAL), eq("ACCRUAL_ENTRY"), eq(20L), specsCaptor.capture(), eq("controller1")))
                .thenAnswer(inv -> postedEntryWithLines(specsCaptor.getValue(), 600L));

        AccrualResponse response = service.createAccrual(balancedRequest());

        assertEquals(2, specsCaptor.getValue().size());
        assertEquals("PENDING_REVERSAL", response.getStatus());
        assertEquals(600L, response.getPostedJournalEntryNumber());
        assertNull(response.getReversalJournalEntryId());
    }

    @Test
    void createAccrualRejectsADuplicateReference() {
        when(accrualEntryRepository.existsByReference("ACR-2026-001")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> service.createAccrual(balancedRequest()));
        verifyNoInteractions(glPostingService);
    }

    @Test
    void createAccrualRejectsAReversalDateBeforeTheAccrualDate() {
        CreateAccrualRequest request = balancedRequest();
        request.setReversalDate(LocalDate.of(2026, 8, 1)); // before accrualDate 2026-08-31
        when(accrualEntryRepository.existsByReference("ACR-2026-001")).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> service.createAccrual(request));
        verifyNoInteractions(glPostingService);
        verify(accrualEntryRepository, never()).save(any());
    }

    private AccrualEntry pendingAccrual() {
        JournalEntry original = JournalEntry.builder().id(600L).entryNumber(600L).build();
        return AccrualEntry.builder().id(20L).reference("ACR-2026-001")
                .accrualDate(LocalDate.of(2026, 8, 31)).reversalDate(LocalDate.of(2026, 9, 1))
                .description("Accrue August rent").status(AccrualStatus.PENDING_REVERSAL)
                .createdBy(user).postedJournalEntry(original).build();
    }

    @Test
    void reverseAccrualCallsGlPostingReverseAndMarksReversed() {
        AccrualEntry header = pendingAccrual();
        when(accrualEntryRepository.findById(20L)).thenReturn(Optional.of(header));
        when(userAccountRepository.findById(1L)).thenReturn(Optional.of(user));
        JournalEntry reversal = JournalEntry.builder().id(601L).entryNumber(601L).build();
        when(glPostingService.reverse(header.getPostedJournalEntry(), header.getReversalDate(),
                "Accrual reversal", "controller1")).thenReturn(reversal);
        when(accrualEntryRepository.save(any(AccrualEntry.class))).thenAnswer(inv -> inv.getArgument(0));

        AccrualResponse response = service.reverseAccrual(20L, 1L);

        assertEquals("REVERSED", response.getStatus());
        assertEquals(601L, response.getReversalJournalEntryNumber());
        assertNotNull(response.getReversedAt());
    }

    @Test
    void reverseAccrualRejectsAnAlreadyReversedAccrual() {
        AccrualEntry header = pendingAccrual();
        header.setStatus(AccrualStatus.REVERSED);
        when(accrualEntryRepository.findById(20L)).thenReturn(Optional.of(header));

        assertThrows(IllegalStateException.class, () -> service.reverseAccrual(20L, 1L));
        verifyNoInteractions(glPostingService);
    }

    @Test
    void reverseDueAccrualsOnlyReversesEntriesWhoseReversalDateHasArrived() {
        AccrualEntry due = pendingAccrual();
        when(accrualEntryRepository.findByStatusAndReversalDateLessThanEqual(
                AccrualStatus.PENDING_REVERSAL, LocalDate.of(2026, 9, 1))).thenReturn(List.of(due));
        JournalEntry reversal = JournalEntry.builder().id(601L).entryNumber(601L).build();
        when(glPostingService.reverse(due.getPostedJournalEntry(), due.getReversalDate(),
                "Accrual reversal (due)", "SYSTEM")).thenReturn(reversal);
        when(accrualEntryRepository.save(any(AccrualEntry.class))).thenAnswer(inv -> inv.getArgument(0));

        List<AccrualResponse> results = service.reverseDueAccruals(LocalDate.of(2026, 9, 1));

        assertEquals(1, results.size());
        assertEquals("REVERSED", results.get(0).getStatus());
    }
}
