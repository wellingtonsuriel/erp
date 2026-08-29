package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.generalPriceIndex.GeneralPriceIndex;
import com.pos_onlineshop.hybrid.generalPriceIndex.GeneralPriceIndexRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GeneralPriceIndexServiceTest {

    @Mock private GeneralPriceIndexRepository generalPriceIndexRepository;

    private GeneralPriceIndexService service;

    @BeforeEach
    void setUp() {
        service = new GeneralPriceIndexService(generalPriceIndexRepository);
    }

    @Test
    void recordIndexValueRejectsANonPositiveValue() {
        assertThrows(IllegalArgumentException.class,
                () -> service.recordIndexValue(LocalDate.of(2026, 1, 31), BigDecimal.ZERO, "MANUAL"));
        assertThrows(IllegalArgumentException.class,
                () -> service.recordIndexValue(LocalDate.of(2026, 1, 31), new BigDecimal("-5"), "MANUAL"));
        verifyNoInteractions(generalPriceIndexRepository);
    }

    @Test
    void recordIndexValueRejectsADuplicateDate() {
        when(generalPriceIndexRepository.existsByIndexDate(LocalDate.of(2026, 1, 31))).thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> service.recordIndexValue(LocalDate.of(2026, 1, 31), new BigDecimal("100.00"), "MANUAL"));
        verify(generalPriceIndexRepository, never()).save(any());
    }

    @Test
    void recordIndexValueSavesAValidReading() {
        when(generalPriceIndexRepository.existsByIndexDate(LocalDate.of(2026, 1, 31))).thenReturn(false);
        when(generalPriceIndexRepository.save(any(GeneralPriceIndex.class))).thenAnswer(inv -> inv.getArgument(0));

        GeneralPriceIndex result = service.recordIndexValue(LocalDate.of(2026, 1, 31), new BigDecimal("100.00"), "ZIMSTAT");

        assertEquals(LocalDate.of(2026, 1, 31), result.getIndexDate());
        assertEquals(0, new BigDecimal("100.00").compareTo(result.getIndexValue()));
        assertEquals("ZIMSTAT", result.getSource());
    }

    @Test
    void getConversionFactorDividesTheLaterIndexByTheEarlierOne() {
        when(generalPriceIndexRepository.findFirstByIndexDateLessThanEqualOrderByIndexDateDesc(LocalDate.of(2026, 1, 31)))
                .thenReturn(Optional.of(GeneralPriceIndex.builder().indexDate(LocalDate.of(2026, 1, 31)).indexValue(new BigDecimal("100.00")).build()));
        when(generalPriceIndexRepository.findFirstByIndexDateLessThanEqualOrderByIndexDateDesc(LocalDate.of(2026, 3, 31)))
                .thenReturn(Optional.of(GeneralPriceIndex.builder().indexDate(LocalDate.of(2026, 3, 31)).indexValue(new BigDecimal("150.00")).build()));

        BigDecimal factor = service.getConversionFactor(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 3, 31));

        assertEquals(0, new BigDecimal("1.50000000").compareTo(factor));
    }

    @Test
    void getConversionFactorFallsBackToTheMostRecentReadingOnOrBeforeEachDate() {
        LocalDate requestedDate = LocalDate.of(2026, 2, 15); // no reading published for this exact date
        when(generalPriceIndexRepository.findFirstByIndexDateLessThanEqualOrderByIndexDateDesc(requestedDate))
                .thenReturn(Optional.of(GeneralPriceIndex.builder().indexDate(LocalDate.of(2026, 1, 31)).indexValue(new BigDecimal("100.00")).build()));
        when(generalPriceIndexRepository.findFirstByIndexDateLessThanEqualOrderByIndexDateDesc(LocalDate.of(2026, 1, 31)))
                .thenReturn(Optional.of(GeneralPriceIndex.builder().indexDate(LocalDate.of(2026, 1, 31)).indexValue(new BigDecimal("100.00")).build()));

        BigDecimal factor = service.getConversionFactor(LocalDate.of(2026, 1, 31), requestedDate);

        assertEquals(0, new BigDecimal("1.00000000").compareTo(factor));
    }

    @Test
    void getConversionFactorThrowsWhenNoIndexDataCoversTheRequestedDate() {
        when(generalPriceIndexRepository.findFirstByIndexDateLessThanEqualOrderByIndexDateDesc(any()))
                .thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class,
                () -> service.getConversionFactor(LocalDate.of(2020, 1, 1), LocalDate.of(2026, 1, 1)));
    }
}
