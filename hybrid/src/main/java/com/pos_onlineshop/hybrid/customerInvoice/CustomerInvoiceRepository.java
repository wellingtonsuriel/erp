package com.pos_onlineshop.hybrid.customerInvoice;

import com.pos_onlineshop.hybrid.customers.Customers;
import com.pos_onlineshop.hybrid.enums.CustomerInvoiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerInvoiceRepository extends JpaRepository<CustomerInvoice, Long> {

    Optional<CustomerInvoice> findByInvoiceNumber(String invoiceNumber);

    boolean existsByInvoiceNumber(String invoiceNumber);

    List<CustomerInvoice> findByCustomer(Customers customer);

    List<CustomerInvoice> findByCustomerAndStatusIn(Customers customer, List<CustomerInvoiceStatus> statuses);

    List<CustomerInvoice> findByStatusIn(List<CustomerInvoiceStatus> statuses);
}
