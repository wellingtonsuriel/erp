package com.pos_onlineshop.hybrid.salesReturn;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SalesReturnLineRepository extends JpaRepository<SalesReturnLine, Long> {

    @Query("SELECT COALESCE(SUM(l.quantityReturned), 0) FROM SalesReturnLine l WHERE l.orderLine.id = :orderLineId")
    int sumQuantityReturnedByOrderLineId(Long orderLineId);
}
