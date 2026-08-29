package com.pos_onlineshop.hybrid.bankCharge;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BankChargeRepository extends JpaRepository<BankCharge, Long> {
    boolean existsByReferenceNumber(String referenceNumber);

    java.util.List<BankCharge> findAllByOrderByIdDesc();
}
