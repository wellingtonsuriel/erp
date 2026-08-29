package com.pos_onlineshop.hybrid.journalLine;

import com.pos_onlineshop.hybrid.account.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Every aggregate query here sums JournalLine.baseAmount (grouped by which side - debit or
 * credit - each line is on), never the raw transaction-currency debitAmount/creditAmount.
 * baseAmount is the authoritative accounting value throughout this GL - see JournalValidator's
 * class comment for why a raw-amount sum cannot correctly represent a genuinely multi-currency
 * entry. Every report/reconciliation service that consumes these queries (TrialBalanceService,
 * BalanceSheetService, CashFlowService, ProfitAndLossService, VatReturnService,
 * ControlAccountReconciliationService, AccountingPeriodService's period-close sweep) inherits
 * this correction automatically, with no change needed on their side.
 */
@Repository
public interface JournalLineRepository extends JpaRepository<JournalLine, Long> {

    List<JournalLine> findByAccount(Account account);

    boolean existsByAccount(Account account);

    @Query("SELECT COALESCE(SUM(CASE WHEN l.debitAmount > 0 THEN l.baseAmount ELSE 0 END), 0) FROM JournalLine l " +
            "WHERE l.account = :account AND l.journalEntry.entryDate BETWEEN :from AND :to " +
            "AND l.journalEntry.status = com.pos_onlineshop.hybrid.enums.JournalStatus.POSTED")
    BigDecimal sumDebitsForAccountBetween(@Param("account") Account account,
                                           @Param("from") LocalDate from,
                                           @Param("to") LocalDate to);

    @Query("SELECT COALESCE(SUM(CASE WHEN l.creditAmount > 0 THEN l.baseAmount ELSE 0 END), 0) FROM JournalLine l " +
            "WHERE l.account = :account AND l.journalEntry.entryDate BETWEEN :from AND :to " +
            "AND l.journalEntry.status = com.pos_onlineshop.hybrid.enums.JournalStatus.POSTED")
    BigDecimal sumCreditsForAccountBetween(@Param("account") Account account,
                                            @Param("from") LocalDate from,
                                            @Param("to") LocalDate to);

    /** Per-account [accountId, sumDebit, sumCredit] (base currency) for all POSTED activity
     * strictly before beforeDate. */
    @Query("SELECT l.account.id, " +
            "COALESCE(SUM(CASE WHEN l.debitAmount > 0 THEN l.baseAmount ELSE 0 END), 0), " +
            "COALESCE(SUM(CASE WHEN l.creditAmount > 0 THEN l.baseAmount ELSE 0 END), 0) " +
            "FROM JournalLine l " +
            "WHERE l.journalEntry.status = com.pos_onlineshop.hybrid.enums.JournalStatus.POSTED " +
            "AND l.journalEntry.entryDate < :beforeDate " +
            "AND (:shopId IS NULL OR l.costCenterShop.id = :shopId) " +
            "GROUP BY l.account.id")
    List<Object[]> aggregateBeforeDate(@Param("beforeDate") LocalDate beforeDate, @Param("shopId") Long shopId);

    /** Per-account [accountId, sumDebit, sumCredit] (base currency) for all POSTED activity
     * within [from, to]. */
    @Query("SELECT l.account.id, " +
            "COALESCE(SUM(CASE WHEN l.debitAmount > 0 THEN l.baseAmount ELSE 0 END), 0), " +
            "COALESCE(SUM(CASE WHEN l.creditAmount > 0 THEN l.baseAmount ELSE 0 END), 0) " +
            "FROM JournalLine l " +
            "WHERE l.journalEntry.status = com.pos_onlineshop.hybrid.enums.JournalStatus.POSTED " +
            "AND l.journalEntry.entryDate BETWEEN :from AND :to " +
            "AND (:shopId IS NULL OR l.costCenterShop.id = :shopId) " +
            "GROUP BY l.account.id")
    List<Object[]> aggregateBetween(@Param("from") LocalDate from, @Param("to") LocalDate to, @Param("shopId") Long shopId);

    /** [sourceReferenceType, sumDebit, sumCredit] (base currency) for POSTED lines against one
     * of the given accounts within [from, to] - used by CashFlowService to break down
     * cash-account activity by the business event that moved it, since JournalEntry does not
     * store a FinancialEventType. */
    @Query("SELECT COALESCE(l.journalEntry.sourceReferenceType, 'OTHER'), " +
            "COALESCE(SUM(CASE WHEN l.debitAmount > 0 THEN l.baseAmount ELSE 0 END), 0), " +
            "COALESCE(SUM(CASE WHEN l.creditAmount > 0 THEN l.baseAmount ELSE 0 END), 0) " +
            "FROM JournalLine l " +
            "WHERE l.account.id IN :accountIds " +
            "AND l.journalEntry.status = com.pos_onlineshop.hybrid.enums.JournalStatus.POSTED " +
            "AND l.journalEntry.entryDate BETWEEN :from AND :to " +
            "AND (:shopId IS NULL OR l.costCenterShop.id = :shopId) " +
            "GROUP BY l.journalEntry.sourceReferenceType")
    List<Object[]> aggregateBySourceReferenceTypeForAccounts(@Param("accountIds") List<Long> accountIds,
                                                               @Param("from") LocalDate from,
                                                               @Param("to") LocalDate to,
                                                               @Param("shopId") Long shopId);

    /** [sourceReferenceType, sourceReferenceId, sumDebits] (base currency) per business event
     * in range - total debits of a balanced entry equals its gross value (e.g. the Cash debit
     * of a POS sale), making it comparable to
     * AccountancyEntryRepository.aggregateDebitsByReferenceBetween's legacy-side DEBIT total
     * (also base currency) for the same event. Used by LegacyGlReconciliationService. */
    @Query("SELECT l.journalEntry.sourceReferenceType, l.journalEntry.sourceReferenceId, " +
            "SUM(CASE WHEN l.debitAmount > 0 THEN l.baseAmount ELSE 0 END) " +
            "FROM JournalLine l " +
            "WHERE l.journalEntry.status = com.pos_onlineshop.hybrid.enums.JournalStatus.POSTED " +
            "AND l.journalEntry.sourceReferenceType IS NOT NULL AND l.journalEntry.sourceReferenceId IS NOT NULL " +
            "AND l.journalEntry.entryDate BETWEEN :from AND :to " +
            "GROUP BY l.journalEntry.sourceReferenceType, l.journalEntry.sourceReferenceId")
    List<Object[]> aggregateDebitsBySourceReferenceBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Every individual POSTED line for one account within [from, to], oldest first (ties
     * broken by entry id then line id, so same-day entries stay in posting order) - the raw
     * material GeneralLedgerService's account-ledger drill-down builds a running balance
     * from. Never returns a DRAFT/REVERSED entry's lines. */
    @Query("SELECT l FROM JournalLine l " +
            "WHERE l.account = :account AND l.journalEntry.status = com.pos_onlineshop.hybrid.enums.JournalStatus.POSTED " +
            "AND l.journalEntry.entryDate BETWEEN :from AND :to " +
            "AND (:shopId IS NULL OR l.costCenterShop.id = :shopId) " +
            "ORDER BY l.journalEntry.entryDate ASC, l.journalEntry.id ASC, l.id ASC")
    List<JournalLine> findLedgerLinesForAccountBetween(@Param("account") Account account,
                                                         @Param("from") LocalDate from,
                                                         @Param("to") LocalDate to,
                                                         @Param("shopId") Long shopId);
}
