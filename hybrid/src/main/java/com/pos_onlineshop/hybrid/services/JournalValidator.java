package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.enums.GLSourceModule;
import com.pos_onlineshop.hybrid.gl.ClosedPeriodException;
import com.pos_onlineshop.hybrid.gl.JournalImbalanceException;
import com.pos_onlineshop.hybrid.journalEntry.JournalEntry;
import com.pos_onlineshop.hybrid.journalLine.JournalLine;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Pure domain logic - no repository access, no Spring context required to unit test.
 * Enforces the invariants a real GL cannot compromise on: every posted entry balances,
 * every line touches exactly one side of exactly one active account, and nothing posts
 * into a closed period or directly into a control account from a manual entry.
 *
 * Balance is checked in baseAmount (the base-currency value each line already carries),
 * not the raw transaction-currency debitAmount/creditAmount. Every other line in the GL
 * that used to read raw debit/credit for anything but display purposes (see
 * JournalLineRepository's aggregate queries) was fixed the same way, for the same reason:
 * a genuinely multi-currency entry - one whose lines are denominated in different
 * currencies at different rates - only balances in base terms, never in raw terms, so a
 * raw-amount check would either wrongly reject a valid multi-currency entry or (worse)
 * wrongly accept lines that don't actually represent equal economic value. A same-currency
 * entry (the overwhelming majority today) balances identically either way, since baseAmount
 * is just rawAmount * exchangeRate with one exchangeRate shared across the entry - so this
 * is a strict widening of what validates, not a behavior change for existing traffic.
 */
@Component
public class JournalValidator {

    public void validate(JournalEntry entry) {
        List<JournalLine> lines = entry.getLines();

        if (lines == null || lines.size() < 2) {
            throw new JournalImbalanceException(
                    "Journal entry must have at least two lines, got " + (lines == null ? 0 : lines.size()));
        }

        BigDecimal totalDebits = BigDecimal.ZERO;
        BigDecimal totalCredits = BigDecimal.ZERO;

        for (JournalLine line : lines) {
            validateLine(entry, line);
            BigDecimal base = nvl(line.getBaseAmount());
            boolean isDebitLine = nvl(line.getDebitAmount()).compareTo(BigDecimal.ZERO) > 0;
            if (isDebitLine) {
                totalDebits = totalDebits.add(base);
            } else {
                totalCredits = totalCredits.add(base);
            }
        }

        if (totalDebits.compareTo(totalCredits) != 0) {
            throw new JournalImbalanceException(
                    "Journal entry does not balance in base currency: debits=" + totalDebits + " credits=" + totalCredits);
        }

        if (entry.getAccountingPeriod() == null || !entry.getAccountingPeriod().acceptsPosting()) {
            throw new ClosedPeriodException(
                    "Accounting period " + (entry.getAccountingPeriod() == null ? "(none)"
                            : entry.getAccountingPeriod().getName()) + " does not accept posting");
        }
    }

    private void validateLine(JournalEntry entry, JournalLine line) {
        if (line.getAccount() == null) {
            throw new JournalImbalanceException("Journal line is missing an account");
        }
        if (!line.getAccount().isActive()) {
            throw new JournalImbalanceException(
                    "Account " + line.getAccount().getCode() + " is inactive and cannot be posted to");
        }

        BigDecimal debit = nvl(line.getDebitAmount());
        BigDecimal credit = nvl(line.getCreditAmount());

        if (debit.compareTo(BigDecimal.ZERO) < 0 || credit.compareTo(BigDecimal.ZERO) < 0) {
            throw new JournalImbalanceException(
                    "Journal line for account " + line.getAccount().getCode() + " cannot carry a negative amount");
        }

        boolean hasDebit = debit.compareTo(BigDecimal.ZERO) > 0;
        boolean hasCredit = credit.compareTo(BigDecimal.ZERO) > 0;
        if (hasDebit == hasCredit) {
            throw new JournalImbalanceException(
                    "Journal line for account " + line.getAccount().getCode()
                            + " must have exactly one of debit/credit set, not " + (hasDebit ? "both" : "neither"));
        }

        if (line.getAccount().isControlAccount() && entry.getSourceModule() == GLSourceModule.MANUAL) {
            throw new JournalImbalanceException(
                    "Control account " + line.getAccount().getCode()
                            + " cannot be posted to directly from a manual entry");
        }
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
