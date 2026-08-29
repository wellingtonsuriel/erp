package com.pos_onlineshop.hybrid.bankAccount;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BankAccountRepository extends JpaRepository<BankAccount, Long> {
    boolean existsByAccountName(String accountName);

    List<BankAccount> findByActiveTrue();
}
