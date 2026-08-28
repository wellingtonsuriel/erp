package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.account.Account;
import com.pos_onlineshop.hybrid.accountingPeriod.AccountingPeriod;
import com.pos_onlineshop.hybrid.enums.AccountType;
import com.pos_onlineshop.hybrid.enums.DebitCredit;
import com.pos_onlineshop.hybrid.enums.GLSourceModule;
import com.pos_onlineshop.hybrid.enums.PeriodStatus;
import com.pos_onlineshop.hybrid.gl.ClosedPeriodException;
import com.pos_onlineshop.hybrid.gl.JournalImbalanceException;
import com.pos_onlineshop.hybrid.journalEntry.JournalEntry;
import com.pos_onlineshop.hybrid.journalLine.JournalLine;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure unit tests - no Spring context, no database. Proves the invariants a real GL
 * cannot compromise on (see GLPostingService's class comment / the GL design report §2.3).
 */
class JournalValidatorTest {

    private final JournalValidator validator = new JournalValidator();

    private Account account(String code, boolean control, boolean active) {
        return Account.builder()
                .id(1L)
                .code(code)
                .name(code + " test account")
                .accountType(AccountType.ASSET)
                .normalBalance(DebitCredit.DEBIT)
                .controlAccount(control)
                .active(active)
                .build();
    }

    private AccountingPeriod openPeriod() {
        return AccountingPeriod.builder()
                .id(1L).name("2026-08")
                .startDate(LocalDate.of(2026, 8, 1))
                .endDate(LocalDate.of(2026, 8, 31))
                .status(PeriodStatus.OPEN)
                .build();
    }

    private JournalEntry entryWithPeriod(AccountingPeriod period, GLSourceModule module) {
        return JournalEntry.builder()
                .entryNumber(1L)
                .idempotencyKey("TEST-1")
                .entryDate(LocalDate.of(2026, 8, 15))
                .accountingPeriod(period)
                .sourceModule(module)
                .build();
    }

    private JournalLine line(Account acc, BigDecimal debit, BigDecimal credit) {
        return JournalLine.builder()
                .account(acc)
                .debitAmount(debit)
                .creditAmount(credit)
                .exchangeRate(BigDecimal.ONE)
                .baseAmount(debit.compareTo(BigDecimal.ZERO) > 0 ? debit : credit)
                .build();
    }

    @Test
    void balancedTwoLineEntryIsAccepted() {
        JournalEntry entry = entryWithPeriod(openPeriod(), GLSourceModule.POS);
        entry.addLine(line(account("1010", false, true), new BigDecimal("100.00"), BigDecimal.ZERO));
        entry.addLine(line(account("4000", false, true), BigDecimal.ZERO, new BigDecimal("100.00")));

        assertDoesNotThrow(() -> validator.validate(entry));
    }

    @Test
    void balancedFiveLineEntryIsAccepted() {
        // POS cash sale shape: Cash 115 | Revenue 100 + VAT 15, COGS 60 | Inventory 60
        JournalEntry entry = entryWithPeriod(openPeriod(), GLSourceModule.POS);
        entry.addLine(line(account("1010", false, true), new BigDecimal("115.00"), BigDecimal.ZERO));
        entry.addLine(line(account("4000", false, true), BigDecimal.ZERO, new BigDecimal("100.00")));
        entry.addLine(line(account("2200", false, true), BigDecimal.ZERO, new BigDecimal("15.00")));
        entry.addLine(line(account("5000", false, true), new BigDecimal("60.00"), BigDecimal.ZERO));
        entry.addLine(line(account("1200", true, true), BigDecimal.ZERO, new BigDecimal("60.00")));

        assertDoesNotThrow(() -> validator.validate(entry));
    }

    @Test
    void unbalancedEntryIsRejected() {
        JournalEntry entry = entryWithPeriod(openPeriod(), GLSourceModule.POS);
        entry.addLine(line(account("1010", false, true), new BigDecimal("100.00"), BigDecimal.ZERO));
        entry.addLine(line(account("4000", false, true), BigDecimal.ZERO, new BigDecimal("90.00")));

        assertThrows(JournalImbalanceException.class, () -> validator.validate(entry));
    }

    @Test
    void singleLineEntryIsRejected() {
        JournalEntry entry = entryWithPeriod(openPeriod(), GLSourceModule.POS);
        entry.addLine(line(account("1010", false, true), new BigDecimal("100.00"), BigDecimal.ZERO));

        assertThrows(JournalImbalanceException.class, () -> validator.validate(entry));
    }

    @Test
    void negativeAmountIsRejected() {
        JournalEntry entry = entryWithPeriod(openPeriod(), GLSourceModule.POS);
        entry.addLine(line(account("1010", false, true), new BigDecimal("-100.00"), BigDecimal.ZERO));
        entry.addLine(line(account("4000", false, true), BigDecimal.ZERO, new BigDecimal("-100.00")));

        assertThrows(JournalImbalanceException.class, () -> validator.validate(entry));
    }

    @Test
    void lineWithBothDebitAndCreditIsRejected() {
        JournalEntry entry = entryWithPeriod(openPeriod(), GLSourceModule.POS);
        entry.addLine(line(account("1010", false, true), new BigDecimal("100.00"), new BigDecimal("100.00")));
        entry.addLine(line(account("4000", false, true), BigDecimal.ZERO, new BigDecimal("100.00")));

        assertThrows(JournalImbalanceException.class, () -> validator.validate(entry));
    }

    @Test
    void lineWithNeitherDebitNorCreditIsRejected() {
        JournalEntry entry = entryWithPeriod(openPeriod(), GLSourceModule.POS);
        entry.addLine(line(account("1010", false, true), BigDecimal.ZERO, BigDecimal.ZERO));
        entry.addLine(line(account("4000", false, true), BigDecimal.ZERO, new BigDecimal("100.00")));

        assertThrows(JournalImbalanceException.class, () -> validator.validate(entry));
    }

    @Test
    void inactiveAccountIsRejected() {
        JournalEntry entry = entryWithPeriod(openPeriod(), GLSourceModule.POS);
        entry.addLine(line(account("1010", false, false), new BigDecimal("100.00"), BigDecimal.ZERO));
        entry.addLine(line(account("4000", false, true), BigDecimal.ZERO, new BigDecimal("100.00")));

        assertThrows(JournalImbalanceException.class, () -> validator.validate(entry));
    }

    @Test
    void closedPeriodIsRejected() {
        AccountingPeriod closed = openPeriod();
        closed.setStatus(PeriodStatus.CLOSED);
        JournalEntry entry = entryWithPeriod(closed, GLSourceModule.POS);
        entry.addLine(line(account("1010", false, true), new BigDecimal("100.00"), BigDecimal.ZERO));
        entry.addLine(line(account("4000", false, true), BigDecimal.ZERO, new BigDecimal("100.00")));

        assertThrows(ClosedPeriodException.class, () -> validator.validate(entry));
    }

    @Test
    void manualPostingToControlAccountIsRejected() {
        JournalEntry entry = entryWithPeriod(openPeriod(), GLSourceModule.MANUAL);
        entry.addLine(line(account("1200", true, true), new BigDecimal("100.00"), BigDecimal.ZERO));
        entry.addLine(line(account("3900", false, true), BigDecimal.ZERO, new BigDecimal("100.00")));

        assertThrows(JournalImbalanceException.class, () -> validator.validate(entry));
    }

    @Test
    void systemPostingToControlAccountIsAllowed() {
        // POS/INVENTORY/etc are allowed to hit a control account like 1200 Inventory Asset -
        // only a hand-typed MANUAL entry is blocked from touching it directly.
        JournalEntry entry = entryWithPeriod(openPeriod(), GLSourceModule.POS);
        entry.addLine(line(account("5000", false, true), new BigDecimal("60.00"), BigDecimal.ZERO));
        entry.addLine(line(account("1200", true, true), BigDecimal.ZERO, new BigDecimal("60.00")));

        assertDoesNotThrow(() -> validator.validate(entry));
    }
}
