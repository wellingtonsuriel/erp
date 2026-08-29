package com.pos_onlineshop.hybrid.payslip;

import com.pos_onlineshop.hybrid.payrollRun.PayrollRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PayslipRepository extends JpaRepository<Payslip, Long> {
    List<Payslip> findByPayrollRun(PayrollRun payrollRun);

    boolean existsByPayrollRunAndEmployeeId(PayrollRun payrollRun, Long employeeId);
}
