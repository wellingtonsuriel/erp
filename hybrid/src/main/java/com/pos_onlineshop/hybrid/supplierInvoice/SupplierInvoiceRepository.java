package com.pos_onlineshop.hybrid.supplierInvoice;

import com.pos_onlineshop.hybrid.enums.SupplierInvoiceStatus;
import com.pos_onlineshop.hybrid.suppliers.Suppliers;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SupplierInvoiceRepository extends JpaRepository<SupplierInvoice, Long> {

    Optional<SupplierInvoice> findByInvoiceNumber(String invoiceNumber);

    boolean existsByInvoiceNumber(String invoiceNumber);

    List<SupplierInvoice> findBySupplier(Suppliers supplier);

    List<SupplierInvoice> findByStatusIn(List<SupplierInvoiceStatus> statuses);
}
