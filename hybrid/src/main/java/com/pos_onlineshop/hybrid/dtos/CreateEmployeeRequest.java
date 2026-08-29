package com.pos_onlineshop.hybrid.dtos;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class CreateEmployeeRequest {

    @NotBlank(message = "Employee number is required")
    private String employeeNumber;

    @NotBlank(message = "Full name is required")
    private String fullName;

    private String email;

    private LocalDate hireDate;

    /** Omit for an employee not on payroll (e.g. one only ever reimbursed for expenses). */
    @DecimalMin(value = "0.0001", message = "Base salary must be positive if provided")
    private BigDecimal baseSalary;

    /** Required if baseSalary is provided. */
    private Long salaryCurrencyId;
}
