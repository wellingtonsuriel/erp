package com.pos_onlineshop.hybrid.sales;

import com.pos_onlineshop.hybrid.customers.Customers;
import com.pos_onlineshop.hybrid.enums.PaymentMethod;
import com.pos_onlineshop.hybrid.enums.SaleType;
import com.pos_onlineshop.hybrid.products.Product;
import com.pos_onlineshop.hybrid.shop.Shop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SalesRepository extends JpaRepository<Sales, Long> {

    List<Sales> findByShop(Shop shop);

    List<Sales> findByCustomer(Customers customer);

    List<Sales> findByProduct(Product product);

    List<Sales> findBySaleType(SaleType saleType);

    List<Sales> findByPaymentMethod(PaymentMethod paymentMethod);

    List<Sales> findByShopAndSaleType(Shop shop, SaleType saleType);

    List<Sales> findByShopAndPaymentMethod(Shop shop, PaymentMethod paymentMethod);

    @Query("SELECT s FROM Sales s WHERE s.addedAt BETWEEN :startDate AND :endDate")
    List<Sales> findByDateRange(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @Query("SELECT s FROM Sales s WHERE s.shop = :shop AND s.addedAt BETWEEN :startDate AND :endDate")
    List<Sales> findByShopAndDateRange(@Param("shop") Shop shop, @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @Query("SELECT s FROM Sales s WHERE s.customer = :customer AND s.addedAt BETWEEN :startDate AND :endDate")
    List<Sales> findByCustomerAndDateRange(@Param("customer") Customers customer, @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @Query("SELECT SUM(s.quantity * s.unitPrice) FROM Sales s WHERE s.shop = :shop")
    BigDecimal getTotalSalesByShop(@Param("shop") Shop shop);

    @Query("SELECT SUM(s.quantity * s.unitPrice) FROM Sales s WHERE s.shop = :shop AND s.addedAt BETWEEN :startDate AND :endDate")
    BigDecimal getTotalSalesByShopAndDateRange(@Param("shop") Shop shop, @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @Query("SELECT SUM(s.quantity * s.unitPrice) FROM Sales s WHERE s.customer = :customer")
    BigDecimal getTotalSalesByCustomer(@Param("customer") Customers customer);

    @Query("SELECT s FROM Sales s WHERE s.shop = :shop ORDER BY s.addedAt DESC")
    List<Sales> findRecentSalesByShop(@Param("shop") Shop shop);

    @Query("SELECT s FROM Sales s WHERE s.customer = :customer ORDER BY s.addedAt DESC")
    List<Sales> findRecentSalesByCustomer(@Param("customer") Customers customer);

    @Query("SELECT COUNT(s) FROM Sales s WHERE s.shop = :shop AND s.addedAt BETWEEN :startDate AND :endDate")
    Long countSalesByShopAndDateRange(@Param("shop") Shop shop, @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);
}
