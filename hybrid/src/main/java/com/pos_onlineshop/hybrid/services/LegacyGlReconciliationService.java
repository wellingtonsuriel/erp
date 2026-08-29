package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.accountancyEntry.AccountancyEntryRepository;
import com.pos_onlineshop.hybrid.dtos.LegacyGlReconciliationReport;
import com.pos_onlineshop.hybrid.journalLine.JournalLineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reconciles the legacy AccountancyEntry ledger (still written alongside the real GL by
 * POSService/OrderService - see their class comments) against JournalEntry/JournalLine for
 * the same business events, joined on (sourceReferenceType, sourceReferenceId) /
 * (referenceType, referenceId). This is diagnostic tooling for the parallel-posting period,
 * not a migration step: it does not touch either ledger, and does not decide which side is
 * "right" - a variance here is a finding to investigate. Both sides compare in base-currency
 * amounts (AccountancyEntry.baseAmount vs JournalLine.baseAmount, the GL's own authoritative
 * value - see JournalValidator's class comment), so a remaining variance is most plausibly a
 * genuine conversion discrepancy (AccountancyEntry historically converted at whatever rate
 * CurrencyService returned at write time, independently of the GL's own conversion for the
 * same event) or an event type the two systems genuinely don't both cover (e.g. legacy's
 * separate "PAYMENT" and "REFUND" reference types for what the GL posts as a single "ORDER"
 * entry - such rows show up here as missingOnOneSide, which is expected and not a defect by
 * itself). Retiring the legacy writes entirely is Stage 11 (legacy migration), once this
 * report has been run against real data and shown clean.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LegacyGlReconciliationService {

    private record ReferenceKey(String referenceType, Long referenceId) {
    }

    private final AccountancyEntryRepository accountancyEntryRepository;
    private final JournalLineRepository journalLineRepository;

    public LegacyGlReconciliationReport generate(LocalDate fromDate, LocalDate toDate) {
        Map<ReferenceKey, BigDecimal> legacyTotals = toKeyedMap(
                accountancyEntryRepository.aggregateDebitsByReferenceBetween(
                        LocalDateTime.of(fromDate, LocalTime.MIN), LocalDateTime.of(toDate, LocalTime.MAX)));
        Map<ReferenceKey, BigDecimal> glTotals = toKeyedMap(
                journalLineRepository.aggregateDebitsBySourceReferenceBetween(fromDate, toDate));

        java.util.Set<ReferenceKey> allKeys = new java.util.LinkedHashSet<>();
        allKeys.addAll(legacyTotals.keySet());
        allKeys.addAll(glTotals.keySet());

        List<LegacyGlReconciliationReport.Line> lines = new ArrayList<>();
        int matched = 0;
        for (ReferenceKey key : allKeys) {
            BigDecimal legacyAmount = legacyTotals.getOrDefault(key, BigDecimal.ZERO);
            BigDecimal glAmount = glTotals.getOrDefault(key, BigDecimal.ZERO);
            BigDecimal variance = legacyAmount.subtract(glAmount);
            boolean isMatched = variance.compareTo(BigDecimal.ZERO) == 0;
            boolean missingOnOneSide = !legacyTotals.containsKey(key) || !glTotals.containsKey(key);
            if (isMatched) {
                matched++;
            }
            lines.add(LegacyGlReconciliationReport.Line.builder()
                    .referenceType(key.referenceType())
                    .referenceId(key.referenceId())
                    .legacyAmount(legacyAmount)
                    .glAmount(glAmount)
                    .variance(variance)
                    .matched(isMatched)
                    .missingOnOneSide(missingOnOneSide)
                    .build());
        }
        lines.sort(Comparator.comparing(LegacyGlReconciliationReport.Line::getReferenceType)
                .thenComparing(LegacyGlReconciliationReport.Line::getReferenceId));

        return LegacyGlReconciliationReport.builder()
                .fromDate(fromDate)
                .toDate(toDate)
                .totalCompared(lines.size())
                .totalMatched(matched)
                .totalUnmatched(lines.size() - matched)
                .lines(lines)
                .build();
    }

    private Map<ReferenceKey, BigDecimal> toKeyedMap(List<Object[]> rows) {
        Map<ReferenceKey, BigDecimal> map = new LinkedHashMap<>();
        for (Object[] row : rows) {
            String referenceType = (String) row[0];
            Long referenceId = (Long) row[1];
            BigDecimal amount = (BigDecimal) row[2];
            map.put(new ReferenceKey(referenceType, referenceId), amount);
        }
        return map;
    }
}
