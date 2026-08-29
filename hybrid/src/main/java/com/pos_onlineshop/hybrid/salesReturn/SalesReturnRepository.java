package com.pos_onlineshop.hybrid.salesReturn;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SalesReturnRepository extends JpaRepository<SalesReturn, Long> {
    boolean existsByReturnNumber(String returnNumber);

    List<SalesReturn> findAllByOrderByIdDesc();

    List<SalesReturn> findByOrderId(Long orderId);
}
