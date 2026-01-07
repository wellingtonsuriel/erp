package com.pos_onlineshop.hybrid.cashier;

import com.pos_onlineshop.hybrid.enums.CashierRole;
import com.pos_onlineshop.hybrid.shop.Shop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface CashierRepository extends JpaRepository<Cashier, Long> {

    Optional<Cashier> findByUsername(String username);

    Optional<Cashier> findByEmployeeId(String employeeId);
    @Query("SELECT MAX(c.employeeId) FROM Cashier c WHERE c.employeeId LIKE 'EMP%'")
    Optional<String> findMaxEmployeeId();

    Optional<Cashier> findByEmail(String email);

    Optional<Cashier> findByPinCode(String pinCode);

    List<Cashier> findByAssignedShop(Shop shop);

    List<Cashier> findByRole(CashierRole role);

    List<Cashier> findByActiveTrue();

    boolean existsByUsername(String username);

    boolean existsByEmployeeId(String employeeId);

    boolean existsByEmail(String email);

    @Query("SELECT c FROM Cashier c WHERE c.assignedShop.id = :shopId AND c.active = true")
    List<Cashier> findActiveByShopId(@Param("shopId") Long shopId);

    @Query("SELECT c.employeeId FROM Cashier c WHERE c.employeeId LIKE :prefix% ORDER BY c.employeeId DESC")
    Optional<String> findTopByEmployeeIdStartingWithOrderByEmployeeIdDesc(@Param("prefix") String prefix);

    /**
     * Find all cashiers assigned to a specific shop
     */
    List<Cashier> findByAssignedShop_Id(Long shopId);

    /**
     * Find all active cashiers assigned to a specific shop
     */
    @Query("SELECT c FROM Cashier c WHERE c.assignedShop.id = :shopId AND c.active = true")
    List<Cashier> findActiveCashiersByShopId(@Param("shopId") Long shopId);

    /**
     * Find cashiers by shop ID and role
     */
    @Query("SELECT c FROM Cashier c WHERE c.assignedShop.id = :shopId AND c.role = :role")
    List<Cashier> findByShopIdAndRole(@Param("shopId") Long shopId, @Param("role") CashierRole role);

    /**
     * Find shop manager(s) by shop ID
     */
    @Query("SELECT c FROM Cashier c WHERE c.assignedShop.id = :shopId AND c.role IN ('MANAGER', 'SUPERVISOR')")
    List<Cashier> findManagersByShopId(@Param("shopId") Long shopId);

    /**
     * Count cashiers in a shop
     */
    @Query("SELECT COUNT(c) FROM Cashier c WHERE c.assignedShop.id = :shopId AND c.active = true")
    long countActiveCashiersByShopId(@Param("shopId") Long shopId);
}