package com.pos_onlineshop.hybrid.employee;

import com.pos_onlineshop.hybrid.currency.Currency;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A minimal HR record - deliberately separate from UserAccount (login credentials/roles are
 * an authentication concern; being a paid employee is an HR concern, and the two don't
 * always coincide - an employee may never log in, and a system user may not be an employee
 * at all). Originally built for ExpenseService (a real employee reference for
 * reimbursements, not a free-text name); baseSalary/salaryCurrency were added for
 * PayrollService - null on an employee not on payroll (e.g. one only ever reimbursed for
 * expenses).
 */
@Entity
@Table(name = "employees")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"salaryCurrency"})
@ToString(exclude = {"salaryCurrency"})
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_number", nullable = false, unique = true, length = 60)
    private String employeeNumber;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(length = 150)
    private String email;

    @Column(name = "hire_date")
    private LocalDate hireDate;

    @Column(name = "base_salary", precision = 19, scale = 4)
    private BigDecimal baseSalary;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "salary_currency_id")
    private Currency salaryCurrency;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    public boolean isPayrollEligible() {
        return active && baseSalary != null && baseSalary.compareTo(BigDecimal.ZERO) > 0;
    }
}
