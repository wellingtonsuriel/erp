package com.pos_onlineshop.hybrid.employee;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    boolean existsByEmployeeNumber(String employeeNumber);

    Optional<Employee> findByEmployeeNumber(String employeeNumber);

    List<Employee> findByActiveTrue();
}
