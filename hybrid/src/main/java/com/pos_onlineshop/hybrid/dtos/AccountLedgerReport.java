package com.pos_onlineshop.hybrid.dtos;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * A single account's GL detail drill-down: every posted line that touched it within
 * [fromDate, toDate], oldest first, each carrying a running balance - the "account ledger
 * card" / general ledger detail view a trial balance or financial statement line can never
 * show on its own, since those only ever report a period total.
 */
@Data
@Builder
public class AccountLedgerReport {

    private String accountCode;
    private String accountName;
    private String normalBalance;
    private LocalDate fromDate;
    private LocalDate toDate;

    /** Signed per the account's normal balance (e.g. positive for a debit-normal account
     * with a net debit balance) - the same convention every line's runningBalance uses. */
    private BigDecimal openingBalance;
    private BigDecimal closingBalance;

    private List<Line> lines;

    @Data
    @Builder
    public static class Line {
        private LocalDate entryDate;
        private Long journalEntryId;
        private Long journalEntryNumber;
        private String description;
        private String sourceModule;
        private String sourceReferenceType;
        private Long sourceReferenceId;
        private BigDecimal debitAmount;
        private BigDecimal creditAmount;
        private BigDecimal runningBalance;
        private String memo;
    }
}
