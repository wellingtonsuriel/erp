package com.pos_onlineshop.hybrid.purchaseOrder;

import com.pos_onlineshop.hybrid.enums.PurchaseOrderStatus;
import com.pos_onlineshop.hybrid.shop.Shop;
import com.pos_onlineshop.hybrid.suppliers.Suppliers;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    Optional<PurchaseOrder> findByPoNumber(String poNumber);

    Page<PurchaseOrder> findByStatus(PurchaseOrderStatus status, Pageable pageable);

    Page<PurchaseOrder> findBySupplier(Suppliers supplier, Pageable pageable);

    Page<PurchaseOrder> findByShop(Shop shop, Pageable pageable);

    List<PurchaseOrder> findBySupplierAndStatusIn(Suppliers supplier, List<PurchaseOrderStatus> statuses);
}
