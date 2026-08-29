package com.pos_onlineshop.hybrid.payslip;

import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.employee.Employee;
import com.pos_onlineshop.hybrid.payrollRun.PayrollRun;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/** One employee's line within a PayrollRun - a snapshot of what they were paid (grossPay is
 * copied from Employee.baseSalary at run time, not read live from Employee afterward, so a
 * later salary change never rewrites payroll history). Never edited once created - a
 * correction is a new run's adjustment, not a change to this record. */
@Entity
@Table(name = "payslips", uniqueConstraints = @UniqueConstraint(columnNames = {"payroll_run_id", "employee_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"payrollRun", "employee", "currency"})
@ToString(exclude = {"payrollRun", "employee", "currency"})
public class Payslip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payroll_run_id", nullable = false)
    private PayrollRun payrollRun;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "currency_id", nullable = false)
    private Currency currency;

    @Column(name = "gross_pay", nullable = false, precision = 19, scale = 4)
    private BigDecimal grossPay;

    @Column(name = "total_deductions", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalDeductions;

    @Column(name = "net_pay", nullable = false, precision = 19, scale = 4)
    private BigDecimal netPay;
}
