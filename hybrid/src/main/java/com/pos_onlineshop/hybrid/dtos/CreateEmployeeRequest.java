package com.pos_onlineshop.hybrid.dtos;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CreateEmployeeRequest {

    @NotBlank(message = "Employee number is required")
    private String employeeNumber;

    @NotBlank(message = "Full name is required")
    private String fullName;

    private String email;

    private LocalDate hireDate;
}
