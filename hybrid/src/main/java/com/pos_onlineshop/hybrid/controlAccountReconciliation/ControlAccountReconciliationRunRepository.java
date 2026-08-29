package com.pos_onlineshop.hybrid.controlAccountReconciliation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ControlAccountReconciliationRunRepository extends JpaRepository<ControlAccountReconciliationRun, Long> {
    List<ControlAccountReconciliationRun> findAllByOrderByIdDesc();
}
