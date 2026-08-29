package com.pos_onlineshop.hybrid.manualJournal;

import com.pos_onlineshop.hybrid.account.Account;
import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.enums.AccountType;
import com.pos_onlineshop.hybrid.enums.DebitCredit;
import com.pos_onlineshop.hybrid.enums.ManualJournalStatus;
import com.pos_onlineshop.hybrid.gl.JournalImbalanceException;
import com.pos_onlineshop.hybrid.manualJournalLine.ManualJournalLine;
import com.pos_onlineshop.hybrid.userAccount.UserAccount;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/** Pure entity/state-machine tests - no Spring context, no database. */
class ManualJournalTest {

    private final Account cash = Account.builder().id(1L).code("1010").name("Cash")
            .accountType(AccountType.ASSET).normalBalance(DebitCredit.DEBIT).build();
    private final Account expense = Account.builder().id(2L).code("5300").name("Operating Expenses")
            .accountType(AccountType.EXPENSE).normalBalance(DebitCredit.DEBIT).build();
    private final Currency currency = Currency.builder().id(1L).code("USD").build();

    private final UserAccount preparer = UserAccount.builder().id(1L).username("clerk1").password("x").email("clerk1@test.com").build();
    private final UserAccount approver = UserAccount.builder().id(2L).username("manager1").password("x").email("manager1@test.com").build();

    private ManualJournal balancedDraft() {
        ManualJournal journal = ManualJournal.builder()
                .entryDate(LocalDate.now()).description("Test manual journal").createdBy(preparer).build();
        journal.addLine(ManualJournalLine.builder().account(expense)
                .debitAmount(new BigDecimal("50.00")).creditAmount(BigDecimal.ZERO).currency(currency).build());
        journal.addLine(ManualJournalLine.builder().account(cash)
                .debitAmount(BigDecimal.ZERO).creditAmount(new BigDecimal("50.00")).currency(currency).build());
        return journal;
    }

    @Test
    void validateBalanceAcceptsABalancedTwoLineJournal() {
        assertDoesNotThrow(() -> balancedDraft().validateBalance());
    }

    @Test
    void validateBalanceRejectsFewerThanTwoLines() {
        ManualJournal journal = ManualJournal.builder().entryDate(LocalDate.now()).description("x").createdBy(preparer).build();
        journal.addLine(ManualJournalLine.builder().account(cash)
                .debitAmount(new BigDecimal("10.00")).creditAmount(BigDecimal.ZERO).currency(currency).build());

        assertThrows(JournalImbalanceException.class, journal::validateBalance);
    }

    @Test
    void validateBalanceRejectsUnbalancedLines() {
        ManualJournal journal = balancedDraft();
        journal.getLines().get(1).setCreditAmount(new BigDecimal("40.00"));

        assertThrows(JournalImbalanceException.class, journal::validateBalance);
    }

    @Test
    void validateBalanceRejectsALineWithBothDebitAndCreditSet() {
        ManualJournal journal = balancedDraft();
        journal.getLines().get(0).setCreditAmount(new BigDecimal("50.00"));

        assertThrows(JournalImbalanceException.class, journal::validateBalance);
    }

    @Test
    void validateBalanceAcceptsLinesThatBalanceInBaseCurrencyButNotInRawAmounts() {
        Currency zwg = Currency.builder().id(2L).code("ZWG").build();
        ManualJournal journal = ManualJournal.builder()
                .entryDate(LocalDate.now()).description("Multi-currency test").createdBy(preparer).build();
        journal.addLine(ManualJournalLine.builder().account(cash)
                .debitAmount(new BigDecimal("100.00")).creditAmount(BigDecimal.ZERO)
                .currency(currency).exchangeRate(BigDecimal.ONE).build());
        journal.addLine(ManualJournalLine.builder().account(expense)
                .debitAmount(BigDecimal.ZERO).creditAmount(new BigDecimal("8000.00"))
                .currency(zwg).exchangeRate(new BigDecimal("0.0125")).build());

        assertDoesNotThrow(journal::validateBalance);
    }

    @Test
    void validateBalanceRejectsLinesThatBalanceInRawAmountsButNotInBaseCurrency() {
        Currency zwg = Currency.builder().id(2L).code("ZWG").build();
        ManualJournal journal = ManualJournal.builder()
                .entryDate(LocalDate.now()).description("Multi-currency test").createdBy(preparer).build();
        journal.addLine(ManualJournalLine.builder().account(cash)
                .debitAmount(new BigDecimal("100.00")).creditAmount(BigDecimal.ZERO)
                .currency(currency).exchangeRate(BigDecimal.ONE).build());
        journal.addLine(ManualJournalLine.builder().account(expense)
                .debitAmount(BigDecimal.ZERO).creditAmount(new BigDecimal("100.00"))
                .currency(zwg).exchangeRate(new BigDecimal("0.50")).build());

        assertThrows(JournalImbalanceException.class, journal::validateBalance);
    }

    @Test
    void submitTransitionsDraftToSubmittedAndValidatesBalance() {
        ManualJournal journal = balancedDraft();
        journal.submit(preparer);

        assertEquals(ManualJournalStatus.SUBMITTED, journal.getStatus());
        assertEquals(preparer, journal.getSubmittedBy());
        assertNotNull(journal.getSubmittedAt());
    }

    @Test
    void submitRejectsAnUnbalancedJournal() {
        ManualJournal journal = balancedDraft();
        journal.getLines().get(1).setCreditAmount(new BigDecimal("40.00"));

        assertThrows(JournalImbalanceException.class, () -> journal.submit(preparer));
    }

    @Test
    void cannotSubmitTwice() {
        ManualJournal journal = balancedDraft();
        journal.submit(preparer);

        assertThrows(IllegalStateException.class, () -> journal.submit(preparer));
    }

    @Test
    void approveTransitionsSubmittedToApproved() {
        ManualJournal journal = balancedDraft();
        journal.submit(preparer);
        journal.approve(approver);

        assertEquals(ManualJournalStatus.APPROVED, journal.getStatus());
        assertEquals(approver, journal.getApprovedBy());
    }

    @Test
    void makerCannotApproveTheirOwnJournal() {
        ManualJournal journal = balancedDraft();
        journal.submit(preparer);

        assertThrows(IllegalStateException.class, () -> journal.approve(preparer));
        assertEquals(ManualJournalStatus.SUBMITTED, journal.getStatus());
    }

    @Test
    void cannotApproveADraftJournal() {
        ManualJournal journal = balancedDraft();
        assertThrows(IllegalStateException.class, () -> journal.approve(approver));
    }

    @Test
    void rejectTransitionsSubmittedToRejectedWithReason() {
        ManualJournal journal = balancedDraft();
        journal.submit(preparer);
        journal.reject(approver, "Wrong accounts");

        assertEquals(ManualJournalStatus.REJECTED, journal.getStatus());
        assertEquals("Wrong accounts", journal.getRejectionReason());
        assertEquals(approver, journal.getRejectedBy());
    }

    @Test
    void cannotPostAJournalThatIsNotApproved() {
        ManualJournal journal = balancedDraft();
        journal.submit(preparer);

        assertThrows(IllegalStateException.class, () -> journal.markPosted(null));
    }

    @Test
    void markPostedTransitionsApprovedToPosted() {
        ManualJournal journal = balancedDraft();
        journal.submit(preparer);
        journal.approve(approver);
        journal.markPosted(com.pos_onlineshop.hybrid.journalEntry.JournalEntry.builder().id(99L).entryNumber(1L).build());

        assertEquals(ManualJournalStatus.POSTED, journal.getStatus());
        assertNotNull(journal.getPostedJournalEntry());
    }
}
