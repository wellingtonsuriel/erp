package com.pos_onlineshop.hybrid.tax;

import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.enums.TaxCalculationType;
import com.pos_onlineshop.hybrid.enums.TaxNature;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Tax entity representing different taxes in the system.
 * Supports various tax types including VAT, excise duties, levies, etc.
 */
@Entity
@Table(name = "taxes")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"currency"})
@ToString(exclude = {"currency"})
public class Tax {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tax_id")
    private Long taxId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tax_nature", nullable = false)
    private TaxNature taxNature;   // VAT, EXCISE, LEVY, etc

    @Column(name = "tax_name", nullable = false)
    private String taxName;         // "VAT 15%", "Fast Food Levy"

    @Enumerated(EnumType.STRING)
    @Column(name = "tax_calculation_type", nullable = false)
    private TaxCalculationType taxCalculationType;  // FIXED, PERCENTAGE, TIERED

    /**
     * Used for FIXED and PERCENTAGE tax calculations.
     * - If PERCENTAGE: value of 15 means 15%
     * - If FIXED: value of 2 means $2 (in the specified currency)
     */
    @Column(name = "tax_value", precision = 19, scale = 4)
    private BigDecimal taxValue;

    /**
     * Currency for FIXED taxes. Only meaningful for FIXED tax calculation type.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "currency_id")
    private Currency currency;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Calculate tax amount based on the base amount.
     *
     * @param baseAmount the amount to calculate tax on
     * @return the calculated tax amount
     */
    public BigDecimal calculateTaxAmount(BigDecimal baseAmount) {
        if (baseAmount == null || taxValue == null) {
            return BigDecimal.ZERO;
        }

        return switch (taxCalculationType) {
            case PERCENTAGE -> baseAmount.multiply(taxValue)
                    .divide(BigDecimal.valueOf(100), 4, java.math.RoundingMode.HALF_UP);
            case FIXED -> taxValue;
            case TIERED -> BigDecimal.ZERO; // Tiered calculation would require additional logic
        };
    }
}
