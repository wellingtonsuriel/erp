package com.pos_onlineshop.hybrid.fxRevaluation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FxRevaluationEntryRepository extends JpaRepository<FxRevaluationEntry, Long> {
    List<FxRevaluationEntry> findAllByOrderByIdDesc();
}
