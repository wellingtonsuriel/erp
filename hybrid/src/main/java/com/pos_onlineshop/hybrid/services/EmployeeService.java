package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.dtos.CreateEmployeeRequest;
import com.pos_onlineshop.hybrid.employee.Employee;
import com.pos_onlineshop.hybrid.employee.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class EmployeeService {

    private final EmployeeRepository employeeRepository;

    @Transactional(readOnly = true)
    public List<Employee> findAllActive() {
        return employeeRepository.findByActiveTrue();
    }

    public Employee create(CreateEmployeeRequest request) {
        if (employeeRepository.existsByEmployeeNumber(request.getEmployeeNumber())) {
            throw new IllegalArgumentException("An employee with number " + request.getEmployeeNumber() + " already exists");
        }
        return employeeRepository.save(Employee.builder()
                .employeeNumber(request.getEmployeeNumber())
                .fullName(request.getFullName())
                .email(request.getEmail())
                .hireDate(request.getHireDate())
                .build());
    }
}
