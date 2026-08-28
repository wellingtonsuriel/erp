package com.pos_onlineshop.hybrid.customerReceipt;

import com.pos_onlineshop.hybrid.customerInvoice.CustomerInvoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CustomerReceiptRepository extends JpaRepository<CustomerReceipt, Long> {

    List<CustomerReceipt> findByInvoice(CustomerInvoice invoice);
}
