package com.pos_onlineshop.hybrid.cashBankTransfer;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CashBankTransferRepository extends JpaRepository<CashBankTransfer, Long> {
    boolean existsByReferenceNumber(String referenceNumber);

    java.util.List<CashBankTransfer> findAllByOrderByIdDesc();
}
