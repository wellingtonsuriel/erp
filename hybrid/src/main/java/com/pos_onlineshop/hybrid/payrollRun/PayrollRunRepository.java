package com.pos_onlineshop.hybrid.payrollRun;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PayrollRunRepository extends JpaRepository<PayrollRun, Long> {
    boolean existsByRunNumber(String runNumber);

    List<PayrollRun> findAllByOrderByIdDesc();
}
