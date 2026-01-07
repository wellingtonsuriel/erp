package com.pos_onlineshop.hybrid.orderLines;

import com.pos_onlineshop.hybrid.products.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface OrderLineRepository extends JpaRepository<OrderLine, Long> {

    List<OrderLine> findByProduct(Product product);

    @Query("SELECT ol.product, SUM(ol.quantity) FROM OrderLine ol " +
            "GROUP BY ol.product ORDER BY SUM(ol.quantity) DESC")
    List<Object[]> findMostOrderedProducts();
}