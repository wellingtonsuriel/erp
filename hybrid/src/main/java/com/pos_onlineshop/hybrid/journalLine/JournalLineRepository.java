package com.pos_onlineshop.hybrid.journalLine;

import com.pos_onlineshop.hybrid.account.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface JournalLineRepository extends JpaRepository<JournalLine, Long> {

    List<JournalLine> findByAccount(Account account);

    boolean existsByAccount(Account account);

    @Query("SELECT COALESCE(SUM(l.debitAmount), 0) FROM JournalLine l " +
            "WHERE l.account = :account AND l.journalEntry.entryDate BETWEEN :from AND :to " +
            "AND l.journalEntry.status = com.pos_onlineshop.hybrid.enums.JournalStatus.POSTED")
    BigDecimal sumDebitsForAccountBetween(@Param("account") Account account,
                                           @Param("from") LocalDate from,
                                           @Param("to") LocalDate to);

    @Query("SELECT COALESCE(SUM(l.creditAmount), 0) FROM JournalLine l " +
            "WHERE l.account = :account AND l.journalEntry.entryDate BETWEEN :from AND :to " +
            "AND l.journalEntry.status = com.pos_onlineshop.hybrid.enums.JournalStatus.POSTED")
    BigDecimal sumCreditsForAccountBetween(@Param("account") Account account,
                                            @Param("from") LocalDate from,
                                            @Param("to") LocalDate to);

    /** Per-account [accountId, sumDebit, sumCredit] for all POSTED activity strictly before beforeDate. */
    @Query("SELECT l.account.id, COALESCE(SUM(l.debitAmount), 0), COALESCE(SUM(l.creditAmount), 0) " +
            "FROM JournalLine l " +
            "WHERE l.journalEntry.status = com.pos_onlineshop.hybrid.enums.JournalStatus.POSTED " +
            "AND l.journalEntry.entryDate < :beforeDate " +
            "AND (:shopId IS NULL OR l.costCenterShop.id = :shopId) " +
            "GROUP BY l.account.id")
    List<Object[]> aggregateBeforeDate(@Param("beforeDate") LocalDate beforeDate, @Param("shopId") Long shopId);

    /** Per-account [accountId, sumDebit, sumCredit] for all POSTED activity within [from, to]. */
    @Query("SELECT l.account.id, COALESCE(SUM(l.debitAmount), 0), COALESCE(SUM(l.creditAmount), 0) " +
            "FROM JournalLine l " +
            "WHERE l.journalEntry.status = com.pos_onlineshop.hybrid.enums.JournalStatus.POSTED " +
            "AND l.journalEntry.entryDate BETWEEN :from AND :to " +
            "AND (:shopId IS NULL OR l.costCenterShop.id = :shopId) " +
            "GROUP BY l.account.id")
    List<Object[]> aggregateBetween(@Param("from") LocalDate from, @Param("to") LocalDate to, @Param("shopId") Long shopId);

    /** [sourceReferenceType, sumDebit, sumCredit] for POSTED lines against one of the given
     * accounts within [from, to] - used by CashFlowService to break down cash-account activity
     * by the business event that moved it, since JournalEntry does not store a FinancialEventType. */
    @Query("SELECT COALESCE(l.journalEntry.sourceReferenceType, 'OTHER'), " +
            "COALESCE(SUM(l.debitAmount), 0), COALESCE(SUM(l.creditAmount), 0) " +
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

    /** [sourceReferenceType, sourceReferenceId, sumDebits] per business event in range - total
     * debits of a balanced entry equals its gross value (e.g. the Cash debit of a POS sale),
     * making it comparable to AccountancyEntryRepository.aggregateDebitsByReferenceBetween's
     * legacy-side DEBIT total for the same event. Used by LegacyGlReconciliationService. */
    @Query("SELECT l.journalEntry.sourceReferenceType, l.journalEntry.sourceReferenceId, SUM(l.debitAmount) " +
            "FROM JournalLine l " +
            "WHERE l.journalEntry.status = com.pos_onlineshop.hybrid.enums.JournalStatus.POSTED " +
            "AND l.journalEntry.sourceReferenceType IS NOT NULL AND l.journalEntry.sourceReferenceId IS NOT NULL " +
            "AND l.journalEntry.entryDate BETWEEN :from AND :to " +
            "GROUP BY l.journalEntry.sourceReferenceType, l.journalEntry.sourceReferenceId")
    List<Object[]> aggregateDebitsBySourceReferenceBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
