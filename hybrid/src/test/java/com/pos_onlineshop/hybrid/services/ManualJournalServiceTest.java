package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.account.Account;
import com.pos_onlineshop.hybrid.account.AccountRepository;
import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.currency.CurrencyRepository;
import com.pos_onlineshop.hybrid.dtos.*;
import com.pos_onlineshop.hybrid.enums.AccountType;
import com.pos_onlineshop.hybrid.enums.DebitCredit;
import com.pos_onlineshop.hybrid.enums.ManualJournalStatus;
import com.pos_onlineshop.hybrid.gl.ManualLineSpec;
import com.pos_onlineshop.hybrid.journalEntry.JournalEntry;
import com.pos_onlineshop.hybrid.manualJournal.ManualJournal;
import com.pos_onlineshop.hybrid.manualJournal.ManualJournalRepository;
import com.pos_onlineshop.hybrid.manualJournalLine.ManualJournalLine;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ManualJournalServiceTest {

    @Mock private ManualJournalRepository manualJournalRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private CurrencyRepository currencyRepository;
    @Mock private ShopRepository shopRepository;
    @Mock private UserAccountRepository userAccountRepository;
    @Mock private GLPostingService glPostingService;
    @Mock private CurrencyService currencyService;

    private ManualJournalService service;
    private Account expense;
    private Account cash;
    private Currency currency;
    private UserAccount preparer;
    private UserAccount approver;

    @BeforeEach
    void setUp() {
        service = new ManualJournalService(manualJournalRepository, accountRepository, currencyRepository,
                shopRepository, userAccountRepository, glPostingService, currencyService);

        expense = Account.builder().id(2L).code("5300").name("Operating Expenses")
                .accountType(AccountType.EXPENSE).normalBalance(DebitCredit.DEBIT).build();
        cash = Account.builder().id(1L).code("1010").name("Cash")
                .accountType(AccountType.ASSET).normalBalance(DebitCredit.DEBIT).build();
        currency = Currency.builder().id(1L).code("USD").build();
        preparer = UserAccount.builder().id(1L).username("clerk1").password("x").email("clerk1@test.com").build();
        approver = UserAccount.builder().id(2L).username("manager1").password("x").email("manager1@test.com").build();

        lenient().when(manualJournalRepository.save(any(ManualJournal.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(currencyService.getBaseCurrency()).thenReturn(currency);
    }

    private CreateManualJournalRequest createRequest() {
        ManualJournalLineRequest debit = new ManualJournalLineRequest();
        debit.setAccountId(2L);
        debit.setSide(DebitCredit.DEBIT);
        debit.setAmount(new BigDecimal("50.00"));

        ManualJournalLineRequest credit = new ManualJournalLineRequest();
        credit.setAccountId(1L);
        credit.setSide(DebitCredit.CREDIT);
        credit.setAmount(new BigDecimal("50.00"));

        CreateManualJournalRequest request = new CreateManualJournalRequest();
        request.setEntryDate(LocalDate.now());
        request.setDescription("Accrual correction");
        request.setCreatedByUserId(1L);
        request.setLines(List.of(debit, credit));
        return request;
    }

    @Test
    void createResolvesAccountsAndBuildsABalancedDraft() {
        when(userAccountRepository.findById(1L)).thenReturn(Optional.of(preparer));
        when(accountRepository.findById(2L)).thenReturn(Optional.of(expense));
        when(accountRepository.findById(1L)).thenReturn(Optional.of(cash));

        ManualJournalResponse response = service.create(createRequest());

        assertEquals(ManualJournalStatus.DRAFT.name(), response.getStatus());
        assertEquals(2, response.getLines().size());
        assertEquals("clerk1", response.getCreatedByUsername());
    }

    @Test
    void createThrowsWhenALineAccountDoesNotExist() {
        when(userAccountRepository.findById(1L)).thenReturn(Optional.of(preparer));
        when(accountRepository.findById(2L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.create(createRequest()));
    }

    private ManualJournal storedJournal(Long id) {
        ManualJournal journal = ManualJournal.builder().id(id)
                .entryDate(LocalDate.now()).description("Accrual correction").createdBy(preparer).build();
        journal.addLine(ManualJournalLine.builder().account(expense)
                .debitAmount(new BigDecimal("50.00")).creditAmount(BigDecimal.ZERO).currency(currency).build());
        journal.addLine(ManualJournalLine.builder().account(cash)
                .debitAmount(BigDecimal.ZERO).creditAmount(new BigDecimal("50.00")).currency(currency).build());
        return journal;
    }

    @Test
    void submitResolvesUserAndDelegatesToTheEntity() {
        ManualJournal journal = storedJournal(1L);
        when(manualJournalRepository.findById(1L)).thenReturn(Optional.of(journal));
        when(userAccountRepository.findById(1L)).thenReturn(Optional.of(preparer));

        ManualJournalActionRequest request = new ManualJournalActionRequest();
        request.setUserId(1L);

        ManualJournalResponse response = service.submit(1L, request);

        assertEquals(ManualJournalStatus.SUBMITTED.name(), response.getStatus());
    }

    @Test
    void approveByTheSamePreparerIsRejected() {
        ManualJournal journal = storedJournal(1L);
        journal.submit(preparer);
        when(manualJournalRepository.findById(1L)).thenReturn(Optional.of(journal));
        when(userAccountRepository.findById(1L)).thenReturn(Optional.of(preparer));

        ManualJournalActionRequest request = new ManualJournalActionRequest();
        request.setUserId(1L);

        assertThrows(IllegalStateException.class, () -> service.approve(1L, request));
    }

    @Test
    void approveByADifferentUserSucceeds() {
        ManualJournal journal = storedJournal(1L);
        journal.submit(preparer);
        when(manualJournalRepository.findById(1L)).thenReturn(Optional.of(journal));
        when(userAccountRepository.findById(2L)).thenReturn(Optional.of(approver));

        ManualJournalActionRequest request = new ManualJournalActionRequest();
        request.setUserId(2L);

        ManualJournalResponse response = service.approve(1L, request);

        assertEquals(ManualJournalStatus.APPROVED.name(), response.getStatus());
        assertEquals("manager1", response.getApprovedByUsername());
    }

    @Test
    void postDelegatesToGLPostingServiceWithTheJournalSpecificIdempotencyKeyAndMarksPosted() {
        ManualJournal journal = storedJournal(5L);
        journal.submit(preparer);
        journal.approve(approver);
        when(manualJournalRepository.findById(5L)).thenReturn(Optional.of(journal));
        when(userAccountRepository.findById(2L)).thenReturn(Optional.of(approver));

        JournalEntry postedEntry = JournalEntry.builder().id(50L).entryNumber(10L).build();
        when(glPostingService.postManual(eq("MANUAL-JOURNAL-5"), eq(journal.getEntryDate()),
                eq("Accrual correction"), eq("MANUAL_JOURNAL"), eq(5L), anyList(), eq("manager1")))
                .thenReturn(postedEntry);

        ManualJournalActionRequest request = new ManualJournalActionRequest();
        request.setUserId(2L);

        ManualJournalResponse response = service.post(5L, request);

        assertEquals(ManualJournalStatus.POSTED.name(), response.getStatus());
        assertEquals(10L, response.getPostedJournalEntryNumber());

        ArgumentCaptor<List<ManualLineSpec>> captor = ArgumentCaptor.forClass(List.class);
        verify(glPostingService).postManual(anyString(), any(), anyString(), anyString(), anyLong(), captor.capture(), anyString());
        assertEquals(2, captor.getValue().size());
    }

    @Test
    void postThrowsWhenJournalIsNotApproved() {
        ManualJournal journal = storedJournal(5L);
        when(manualJournalRepository.findById(5L)).thenReturn(Optional.of(journal));

        ManualJournalActionRequest request = new ManualJournalActionRequest();
        request.setUserId(1L);

        assertThrows(IllegalStateException.class, () -> service.post(5L, request));
        verifyNoInteractions(glPostingService);
    }

    @Test
    void rejectRecordsReasonAndActor() {
        ManualJournal journal = storedJournal(1L);
        journal.submit(preparer);
        when(manualJournalRepository.findById(1L)).thenReturn(Optional.of(journal));
        when(userAccountRepository.findById(2L)).thenReturn(Optional.of(approver));

        RejectManualJournalRequest request = new RejectManualJournalRequest();
        request.setUserId(2L);
        request.setReason("Wrong period");

        ManualJournalResponse response = service.reject(1L, request);

        assertEquals(ManualJournalStatus.REJECTED.name(), response.getStatus());
        assertEquals("Wrong period", response.getRejectionReason());
        assertEquals("manager1", response.getRejectedByUsername());
    }
}
