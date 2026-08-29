package com.pos_onlineshop.hybrid.employee;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A minimal HR record - deliberately separate from UserAccount (login credentials/roles are
 * an authentication concern; being a paid employee is an HR concern, and the two don't
 * always coincide - an employee may never log in, and a system user may not be an employee
 * at all). Built here because ExpenseService needs a real employee reference for
 * reimbursements rather than a free-text name; extended with salary/allowance/deduction
 * fields when payroll is built on top of it.
 */
@Entity
@Table(name = "employees")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
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

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
