package com.pos_onlineshop.hybrid.loyaltyTransaction;

import com.pos_onlineshop.hybrid.loyaltyAccount.LoyaltyAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LoyaltyTransactionRepository extends JpaRepository<LoyaltyTransaction, Long> {
    List<LoyaltyTransaction> findByLoyaltyAccountOrderByIdDesc(LoyaltyAccount loyaltyAccount);
}
