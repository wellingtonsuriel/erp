package com.pos_onlineshop.hybrid.repositories;

import com.pos_onlineshop.hybrid.enums.FiscalDeviceType;
import com.pos_onlineshop.hybrid.fiscalDevice.FiscalDevice;
import com.pos_onlineshop.hybrid.shop.Shop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FiscalDeviceRepository extends JpaRepository<FiscalDevice, Long> {

    Optional<FiscalDevice> findBySerialNumber(String serialNumber);

    Optional<FiscalDevice> findByZimraRegistrationNumber(String zimraRegistrationNumber);

    List<FiscalDevice> findByShop(Shop shop);

    List<FiscalDevice> findByShopAndIsActive(Shop shop, Boolean isActive);

    List<FiscalDevice> findByDeviceType(FiscalDeviceType deviceType);

    List<FiscalDevice> findByIsActive(Boolean isActive);

    @Query("SELECT fd FROM FiscalDevice fd WHERE fd.shop = :shop AND fd.isActive = true AND fd.isConnected = true")
    List<FiscalDevice> findOperationalDevicesByShop(@Param("shop") Shop shop);

    @Query("SELECT fd FROM FiscalDevice fd WHERE fd.shop = :shop AND fd.isActive = true AND fd.isConnected = true ORDER BY fd.lastConnectionTime DESC")
    Optional<FiscalDevice> findPrimaryDeviceForShop(@Param("shop") Shop shop);

    @Query("SELECT COUNT(fd) FROM FiscalDevice fd WHERE fd.isActive = true AND fd.isConnected = true")
    Long countActiveConnectedDevices();

    @Query("SELECT COUNT(fd) FROM FiscalDevice fd WHERE fd.shop = :shop AND fd.isActive = true")
    Long countActiveDevicesByShop(@Param("shop") Shop shop);
}
