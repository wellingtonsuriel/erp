package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.account.Account;
import com.pos_onlineshop.hybrid.account.AccountRepository;
import com.pos_onlineshop.hybrid.dtos.AccountLedgerReport;
import com.pos_onlineshop.hybrid.enums.DebitCredit;
import com.pos_onlineshop.hybrid.journalLine.JournalLine;
import com.pos_onlineshop.hybrid.journalLine.JournalLineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * The GL detail drill-down every other report in this package was missing (see
 * GLReportController's former class comment): one account's individual posted lines within a
 * date range, oldest first, each carrying a running balance - not just a period total.
 * openingBalance reuses the same aggregateBeforeDate cumulative-from-inception convention
 * BalanceSheetService/ControlAccountReconciliationService already use, so it agrees with
 * both of those by construction.
 */
@Service
@RequiredArgsConstructor
public class GeneralLedgerService {

    private final AccountRepository accountRepository;
    private final JournalLineRepository journalLineRepository;

    @Transactional(readOnly = true)
    public AccountLedgerReport generateAccountLedger(Long accountId, LocalDate fromDate, LocalDate toDate, Long shopId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
        boolean debitNormal = account.getNormalBalance() == DebitCredit.DEBIT;

        BigDecimal openingDebit = journalLineRepository.sumDebitsForAccountBetween(account, LocalDate.MIN, fromDate.minusDays(1));
        BigDecimal openingCredit = journalLineRepository.sumCreditsForAccountBetween(account, LocalDate.MIN, fromDate.minusDays(1));
        BigDecimal openingBalance = debitNormal ? openingDebit.subtract(openingCredit) : openingCredit.subtract(openingDebit);

        List<JournalLine> journalLines = journalLineRepository.findLedgerLinesForAccountBetween(account, fromDate, toDate, shopId);

        List<AccountLedgerReport.Line> lines = new ArrayList<>();
        BigDecimal runningBalance = openingBalance;
        for (JournalLine journalLine : journalLines) {
            BigDecimal movement = journalLine.getBaseAmount().multiply(sign(journalLine, debitNormal));
            runningBalance = runningBalance.add(movement);

            lines.add(AccountLedgerReport.Line.builder()
                    .entryDate(journalLine.getJournalEntry().getEntryDate())
                    .journalEntryId(journalLine.getJournalEntry().getId())
                    .journalEntryNumber(journalLine.getJournalEntry().getEntryNumber())
                    .description(journalLine.getJournalEntry().getDescription())
                    .sourceModule(journalLine.getJournalEntry().getSourceModule() != null
                            ? journalLine.getJournalEntry().getSourceModule().name() : null)
                    .sourceReferenceType(journalLine.getJournalEntry().getSourceReferenceType())
                    .sourceReferenceId(journalLine.getJournalEntry().getSourceReferenceId())
                    .debitAmount(journalLine.getDebitAmount())
                    .creditAmount(journalLine.getCreditAmount())
                    .runningBalance(runningBalance)
                    .memo(journalLine.getMemo())
                    .build());
        }

        return AccountLedgerReport.builder()
                .accountCode(account.getCode())
                .accountName(account.getName())
                .normalBalance(account.getNormalBalance().name())
                .fromDate(fromDate)
                .toDate(toDate)
                .openingBalance(openingBalance)
                .closingBalance(runningBalance)
                .lines(lines)
                .build();
    }

    /** +1 when this line's side matches the account's normal balance (increases it), -1
     * otherwise (decreases it) - e.g. a debit line on a debit-normal account is +1, a credit
     * line on that same account is -1. */
    private BigDecimal sign(JournalLine line, boolean debitNormal) {
        boolean isDebitLine = line.getDebitAmount() != null && line.getDebitAmount().compareTo(BigDecimal.ZERO) > 0;
        boolean increases = debitNormal == isDebitLine;
        return increases ? BigDecimal.ONE : BigDecimal.ONE.negate();
    }
}
