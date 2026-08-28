package com.pos_onlineshop.hybrid.userAccountPermission;

import com.pos_onlineshop.hybrid.enums.AccountingPermission;
import com.pos_onlineshop.hybrid.userAccount.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserAccountPermissionRepository extends JpaRepository<UserAccountPermission, Long> {

    List<UserAccountPermission> findByUserAccount(UserAccount userAccount);

    Optional<UserAccountPermission> findByUserAccountAndPermission(UserAccount userAccount, AccountingPermission permission);

    boolean existsByUserAccountAndPermission(UserAccount userAccount, AccountingPermission permission);
}
