package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.dtos.CreateEmployeeRequest;
import com.pos_onlineshop.hybrid.employee.Employee;
import com.pos_onlineshop.hybrid.services.EmployeeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;

    @GetMapping
    @PreAuthorize("hasAuthority('GL_VIEW') or hasRole('ADMIN')")
    public List<Employee> list() {
        return employeeService.findAllActive();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('GL_ADMIN') or hasRole('ADMIN')")
    public ResponseEntity<?> create(@Valid @RequestBody CreateEmployeeRequest request) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(employeeService.create(request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
