package com.pos_onlineshop.hybrid.journalEntry;

import com.pos_onlineshop.hybrid.enums.GLSourceModule;
import com.pos_onlineshop.hybrid.enums.JournalStatus;
import com.pos_onlineshop.hybrid.journalLine.JournalLine;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

/** Criteria filters for GET /api/journal-entries. Every filter is optional and AND-combined. */
public final class JournalEntrySpecifications {

    private JournalEntrySpecifications() {
    }

    public static Specification<JournalEntry> withFilters(
            LocalDate fromDate, LocalDate toDate, Long periodId, GLSourceModule sourceModule,
            String sourceReferenceType, Long sourceReferenceId, Long accountId, Long shopId,
            JournalStatus status, Long entryNumber) {

        return (root, query, cb) -> {
            query.distinct(true);
            var conjunction = cb.conjunction();
            if (fromDate != null) {
                conjunction = cb.and(conjunction, cb.greaterThanOrEqualTo(root.get("entryDate"), fromDate));
            }
            if (toDate != null) {
                conjunction = cb.and(conjunction, cb.lessThanOrEqualTo(root.get("entryDate"), toDate));
            }
            if (periodId != null) {
                conjunction = cb.and(conjunction, cb.equal(root.get("accountingPeriod").get("id"), periodId));
            }
            if (sourceModule != null) {
                conjunction = cb.and(conjunction, cb.equal(root.get("sourceModule"), sourceModule));
            }
            if (sourceReferenceType != null && !sourceReferenceType.isBlank()) {
                conjunction = cb.and(conjunction, cb.equal(root.get("sourceReferenceType"), sourceReferenceType));
            }
            if (sourceReferenceId != null) {
                conjunction = cb.and(conjunction, cb.equal(root.get("sourceReferenceId"), sourceReferenceId));
            }
            if (status != null) {
                conjunction = cb.and(conjunction, cb.equal(root.get("status"), status));
            }
            if (entryNumber != null) {
                conjunction = cb.and(conjunction, cb.equal(root.get("entryNumber"), entryNumber));
            }
            if (accountId != null || shopId != null) {
                Join<JournalEntry, JournalLine> lines = root.join("lines", JoinType.INNER);
                if (accountId != null) {
                    conjunction = cb.and(conjunction, cb.equal(lines.get("account").get("id"), accountId));
                }
                if (shopId != null) {
                    conjunction = cb.and(conjunction, cb.equal(lines.get("costCenterShop").get("id"), shopId));
                }
            }
            return conjunction;
        };
    }
}
