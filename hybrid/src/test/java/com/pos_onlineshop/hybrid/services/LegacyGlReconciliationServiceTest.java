package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.accountancyEntry.AccountancyEntryRepository;
import com.pos_onlineshop.hybrid.dtos.LegacyGlReconciliationReport;
import com.pos_onlineshop.hybrid.journalLine.JournalLineRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LegacyGlReconciliationServiceTest {

    @Mock private AccountancyEntryRepository accountancyEntryRepository;
    @Mock private JournalLineRepository journalLineRepository;

    private LegacyGlReconciliationService service;

    private final LocalDate from = LocalDate.of(2026, 8, 1);
    private final LocalDate to = LocalDate.of(2026, 8, 31);

    @BeforeEach
    void setUp() {
        service = new LegacyGlReconciliationService(accountancyEntryRepository, journalLineRepository);
    }

    private void stub(List<Object[]> legacyRows, List<Object[]> glRows) {
        when(accountancyEntryRepository.aggregateDebitsByReferenceBetween(
                LocalDateTime.of(from, LocalTime.MIN), LocalDateTime.of(to, LocalTime.MAX))).thenReturn(legacyRows);
        when(journalLineRepository.aggregateDebitsBySourceReferenceBetween(from, to)).thenReturn(glRows);
    }

    @Test
    void reportsAMatchedLineWhenLegacyAndGlAgreeExactly() {
        stub(List.<Object[]>of(new Object[]{"ORDER", 1L, new BigDecimal("100.00")}),
                List.<Object[]>of(new Object[]{"ORDER", 1L, new BigDecimal("100.00")}));

        LegacyGlReconciliationReport report = service.generate(from, to);

        assertEquals(1, report.getTotalCompared());
        assertEquals(1, report.getTotalMatched());
        assertEquals(0, report.getTotalUnmatched());
        LegacyGlReconciliationReport.Line line = report.getLines().get(0);
        assertTrue(line.isMatched());
        assertFalse(line.isMissingOnOneSide());
        assertEquals(0, BigDecimal.ZERO.compareTo(line.getVariance()));
    }

    @Test
    void reportsAVarianceRatherThanSilentlyIgnoringADisagreement() {
        stub(List.<Object[]>of(new Object[]{"ORDER", 1L, new BigDecimal("100.00")}),
                List.<Object[]>of(new Object[]{"ORDER", 1L, new BigDecimal("95.00")}));

        LegacyGlReconciliationReport report = service.generate(from, to);

        LegacyGlReconciliationReport.Line line = report.getLines().get(0);
        assertFalse(line.isMatched());
        assertFalse(line.isMissingOnOneSide());
        assertEquals(0, new BigDecimal("5.00").compareTo(line.getVariance()));
    }

    @Test
    void flagsALegacyOnlyReferenceAsMissingOnOneSideRatherThanAsAZeroVarianceMismatch() {
        // e.g. legacy's separate PAYMENT reference type that the GL never posts under
        stub(List.<Object[]>of(new Object[]{"PAYMENT", 5L, new BigDecimal("50.00")}),
                List.of());

        LegacyGlReconciliationReport report = service.generate(from, to);

        LegacyGlReconciliationReport.Line line = report.getLines().get(0);
        assertEquals("PAYMENT", line.getReferenceType());
        assertTrue(line.isMissingOnOneSide());
        assertFalse(line.isMatched());
        assertEquals(0, BigDecimal.ZERO.compareTo(line.getGlAmount()));
    }

    @Test
    void flagsAGlOnlyReferenceAsMissingOnOneSide() {
        stub(List.of(), List.<Object[]>of(new Object[]{"ORDER", 9L, new BigDecimal("200.00")}));

        LegacyGlReconciliationReport report = service.generate(from, to);

        LegacyGlReconciliationReport.Line line = report.getLines().get(0);
        assertTrue(line.isMissingOnOneSide());
        assertEquals(0, BigDecimal.ZERO.compareTo(line.getLegacyAmount()));
    }

    @Test
    void countsMultipleReferencesIndependently() {
        stub(List.<Object[]>of(
                        new Object[]{"ORDER", 1L, new BigDecimal("100.00")},
                        new Object[]{"ORDER", 2L, new BigDecimal("50.00")}),
                List.<Object[]>of(
                        new Object[]{"ORDER", 1L, new BigDecimal("100.00")},
                        new Object[]{"ORDER", 2L, new BigDecimal("999.00")}));

        LegacyGlReconciliationReport report = service.generate(from, to);

        assertEquals(2, report.getTotalCompared());
        assertEquals(1, report.getTotalMatched());
        assertEquals(1, report.getTotalUnmatched());
    }
}
