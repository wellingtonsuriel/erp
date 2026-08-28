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
}
