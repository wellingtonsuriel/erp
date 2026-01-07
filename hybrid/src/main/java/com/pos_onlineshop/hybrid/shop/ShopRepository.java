package com.pos_onlineshop.hybrid.shop;

import com.pos_onlineshop.hybrid.cashier.Cashier;
import com.pos_onlineshop.hybrid.enums.ShopType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ShopRepository extends JpaRepository<Shop, Long> {

    Optional<Shop> findByCode(String code);

    List<Shop> findByType(ShopType type);

    List<Shop> findByActiveTrue();

    List<Shop> findByActiveTrueAndType(ShopType type);

    @Query("SELECT s FROM Shop s WHERE s.type = 'WAREHOUSE' AND s.active = true")
    Optional<Shop> findActiveWarehouse();

    @Query("SELECT s FROM Shop s WHERE s.manager.id = :managerId OR :cashier MEMBER OF s.managedByCashiers")
    List<Shop> findByManager(@Param("managerId") Long managerId, @Param("cashier") Cashier cashier);

    @Query("SELECT s FROM Shop s WHERE :cashier MEMBER OF s.assignedCashiers")
    Optional<Shop> findByCashier(@Param("cashier") Cashier cashier);

    @Query("SELECT COUNT(s) FROM Shop s WHERE s.type = :type AND s.active = true")
    long countActiveByType(@Param("type") ShopType type);

    boolean existsByCode(String code);
}
