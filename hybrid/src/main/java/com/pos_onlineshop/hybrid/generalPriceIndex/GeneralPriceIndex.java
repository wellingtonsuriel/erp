package com.pos_onlineshop.hybrid.generalPriceIndex;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One published general price index value (e.g. a monthly CPI reading) for a given date -
 * the raw input IAS 29 restatement is built on. See GeneralPriceIndexService's class
 * comment for what this is (and is not yet) used for.
 */
@Entity
@Table(name = "general_price_index")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GeneralPriceIndex {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "index_date", nullable = false, unique = true)
    private LocalDate indexDate;

    @Column(name = "index_value", nullable = false, precision = 19, scale = 6)
    private BigDecimal indexValue;

    /** e.g. "ZIMSTAT", "MANUAL" - where this reading came from. */
    @Column(length = 60)
    private String source;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
