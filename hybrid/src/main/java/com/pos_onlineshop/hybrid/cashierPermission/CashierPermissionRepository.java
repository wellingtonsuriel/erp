package com.pos_onlineshop.hybrid.cashierPermission;

import com.pos_onlineshop.hybrid.cashier.Cashier;
import com.pos_onlineshop.hybrid.enums.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CashierPermissionRepository extends JpaRepository<CashierPermission, Long> {

    List<CashierPermission> findByCashier(Cashier cashier);

    @Query("SELECT cp.permission FROM CashierPermission cp WHERE cp.cashier.id = :cashierId")
    List<Permission> findPermissionsByCashierId(@Param("cashierId") Long cashierId);

    boolean existsByCashierAndPermission(Cashier cashier, Permission permission);
}
