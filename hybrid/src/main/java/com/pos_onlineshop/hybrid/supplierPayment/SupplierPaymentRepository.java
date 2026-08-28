package com.pos_onlineshop.hybrid.supplierPayment;

import com.pos_onlineshop.hybrid.supplierInvoice.SupplierInvoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SupplierPaymentRepository extends JpaRepository<SupplierPayment, Long> {

    List<SupplierPayment> findByInvoice(SupplierInvoice invoice);
}
