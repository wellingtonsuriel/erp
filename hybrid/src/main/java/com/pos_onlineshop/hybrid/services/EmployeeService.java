package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.currency.CurrencyRepository;
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
    private final CurrencyRepository currencyRepository;

    @Transactional(readOnly = true)
    public List<Employee> findAllActive() {
        return employeeRepository.findByActiveTrue();
    }

    public Employee create(CreateEmployeeRequest request) {
        if (employeeRepository.existsByEmployeeNumber(request.getEmployeeNumber())) {
            throw new IllegalArgumentException("An employee with number " + request.getEmployeeNumber() + " already exists");
        }
        Currency salaryCurrency = null;
        if (request.getBaseSalary() != null) {
            if (request.getSalaryCurrencyId() == null) {
                throw new IllegalArgumentException("Salary currency is required when a base salary is provided");
            }
            salaryCurrency = currencyRepository.findById(request.getSalaryCurrencyId())
                    .orElseThrow(() -> new IllegalArgumentException("Currency not found: " + request.getSalaryCurrencyId()));
        }
        return employeeRepository.save(Employee.builder()
                .employeeNumber(request.getEmployeeNumber())
                .fullName(request.getFullName())
                .email(request.getEmail())
                .hireDate(request.getHireDate())
                .baseSalary(request.getBaseSalary())
                .salaryCurrency(salaryCurrency)
                .build());
    }
}
