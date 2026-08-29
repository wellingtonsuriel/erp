package com.pos_onlineshop.hybrid.deductionType;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Extensible payroll deduction configuration - deliberately NOT a hardcoded PAYE/NSSA/AIDS
 * levy calculation (this repository has no authoritative Zimbabwe statutory rules to encode,
 * and guessing them would be worse than not implementing them at all - see the master
 * prompt's explicit instruction not to fake statutory payroll). An admin defines each
 * deduction here (a name, and either a percentage of gross pay or a fixed amount); every
 * ACTIVE deduction applies to every payroll-eligible employee's run - see PayrollService.
 * Known limitation: deductions are global, not per-employee - there is no per-employee
 * opt-out/override in this slice.
 */
@Entity
@Table(name = "deduction_types")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeductionType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "is_percentage", nullable = false)
    private boolean percentage;

    /** Percentage of gross pay (e.g. 5.00 for 5%) when percentage=true. */
    @Column(precision = 9, scale = 4)
    private BigDecimal rate;

    /** Flat amount per payroll run when percentage=false. */
    @Column(name = "fixed_amount", precision = 19, scale = 4)
    private BigDecimal fixedAmount;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    public BigDecimal calculate(BigDecimal grossPay) {
        if (percentage) {
            BigDecimal r = rate != null ? rate : BigDecimal.ZERO;
            return grossPay.multiply(r).divide(BigDecimal.valueOf(100), 4, java.math.RoundingMode.HALF_UP);
        }
        return fixedAmount != null ? fixedAmount : BigDecimal.ZERO;
    }
}
