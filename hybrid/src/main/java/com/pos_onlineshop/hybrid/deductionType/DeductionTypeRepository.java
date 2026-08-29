package com.pos_onlineshop.hybrid.deductionType;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeductionTypeRepository extends JpaRepository<DeductionType, Long> {
    boolean existsByName(String name);

    List<DeductionType> findByActiveTrue();
}
