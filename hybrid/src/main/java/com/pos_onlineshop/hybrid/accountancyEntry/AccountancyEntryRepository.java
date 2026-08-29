package com.pos_onlineshop.hybrid.accountancyEntry;

import com.pos_onlineshop.hybrid.enums.EntryType;
import com.pos_onlineshop.hybrid.userAccount.UserAccount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AccountancyEntryRepository extends JpaRepository<AccountancyEntry, Long> {

    Page<AccountancyEntry> findByUser(UserAccount user, Pageable pageable);

    List<AccountancyEntry> findByType(EntryType type);

    List<AccountancyEntry> findByEntryDateBetween(LocalDateTime startDate, LocalDateTime endDate);

    List<AccountancyEntry> findByReferenceTypeAndReferenceId(String referenceType, Long referenceId);

    @Query("SELECT ae.accountingPeriod, ae.type, SUM(ae.amount) FROM AccountancyEntry ae " +
            "GROUP BY ae.accountingPeriod, ae.type ORDER BY ae.accountingPeriod")
    List<Object[]> getPeriodicSummary();

    @Query("SELECT SUM(CASE WHEN ae.type = 'CREDIT' THEN ae.amount ELSE 0 END) - " +
            "SUM(CASE WHEN ae.type = 'DEBIT' THEN ae.amount ELSE 0 END) " +
            "FROM AccountancyEntry ae WHERE ae.accountingPeriod = :period")
    BigDecimal calculateBalanceForPeriod(@Param("period") String period);

    @Query("SELECT ae.referenceType, COUNT(ae), SUM(ae.amount) FROM AccountancyEntry ae " +
            "WHERE ae.type = :type GROUP BY ae.referenceType")
    List<Object[]> getSummaryByReferenceType(@Param("type") EntryType type);

    /** [referenceType, referenceId, sumAmount] for the DEBIT leg of every double entry in
     * range - the CREDIT leg always carries the identical amount (see
     * AccountancyEntry.createDoubleEntry), so summing only DEBIT avoids double-counting.
     * Used by LegacyGlReconciliationService to compare this legacy ledger's totals against
     * the real GL for the same business events - see that service's class comment. */
    @Query("SELECT ae.referenceType, ae.referenceId, SUM(ae.amount) FROM AccountancyEntry ae " +
            "WHERE ae.type = com.pos_onlineshop.hybrid.enums.EntryType.DEBIT " +
            "AND ae.referenceType IS NOT NULL AND ae.referenceId IS NOT NULL " +
            "AND ae.entryDate BETWEEN :from AND :to " +
            "GROUP BY ae.referenceType, ae.referenceId")
    List<Object[]> aggregateDebitsByReferenceBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}