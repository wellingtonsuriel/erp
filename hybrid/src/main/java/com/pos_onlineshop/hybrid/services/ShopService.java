package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.cashier.Cashier;
import com.pos_onlineshop.hybrid.enums.CashierRole;
import com.pos_onlineshop.hybrid.enums.ShopType;
import com.pos_onlineshop.hybrid.inventoryTransfer.InventoryTransferRepository;
import com.pos_onlineshop.hybrid.shop.Shop;
import com.pos_onlineshop.hybrid.shop.ShopRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ShopService {

    private final ShopRepository shopRepository;
    private final ShopInventoryService shopInventoryService;

    public Shop createShop(Shop shop) {
        if (shopRepository.existsByCode(shop.getCode())) {
            throw new RuntimeException("Shop with code already exists: " + shop.getCode());
        }

        if (shop.getType() == ShopType.WAREHOUSE) {
            // Only one active warehouse allowed
            shopRepository.findActiveWarehouse()
                    .ifPresent(existing -> {
                        throw new RuntimeException("Active warehouse already exists: " + existing.getName());
                    });
        }

        Shop savedShop = shopRepository.save(shop);
        log.info("Created new shop: {} ({}) with currency {}",
                savedShop.getName(), savedShop.getCode(), savedShop.getDefaultCurrency().getCode());
        return savedShop;
    }

    public Optional<Shop> findById(Long id) {
        return shopRepository.findById(id);
    }

    public Optional<Shop> findByCode(String code) {
        return shopRepository.findByCode(code);
    }

    public Optional<Shop> findWarehouse() {
        return shopRepository.findActiveWarehouse();
    }

    public List<Shop> findAllShops() {
        return shopRepository.findAll();
    }

    public List<Shop> findActiveShops() {
        return shopRepository.findByActiveTrue();
    }

    public List<Shop> findByType(ShopType type) {
        return shopRepository.findByActiveTrueAndType(type);
    }

    public List<Shop> findManagedShops(Cashier cashier) {
        return shopRepository.findByManager(cashier.getId(), cashier);
    }

    public Shop updateShop(Long id, Shop shopDetails) {
        return shopRepository.findById(id)
                .map(shop -> {
                    shop.setName(shopDetails.getName());
                    shop.setAddress(shopDetails.getAddress());
                    shop.setPhoneNumber(shopDetails.getPhoneNumber());
                    shop.setEmail(shopDetails.getEmail());
                    shop.setOpeningTime(shopDetails.getOpeningTime());
                    shop.setClosingTime(shopDetails.getClosingTime());
                    shop.setActive(shopDetails.isActive());
                    shop.setMaxCashiers(shopDetails.getMaxCashiers());
                    shop.setStorageCapacity(shopDetails.getStorageCapacity());

                    if (shopDetails.getManager() != null) {
                        validateManager(shopDetails.getManager());
                        shop.setManager(shopDetails.getManager());
                    }

                    if (shopDetails.getDefaultCurrency() != null) {
                        shop.setDefaultCurrency(shopDetails.getDefaultCurrency());
                    }

                    return shopRepository.save(shop);
                })
                .orElseThrow(() -> new RuntimeException("Shop not found: " + id));
    }

//    @Transactional
//    public void assignCashierToShop(Cashier cashier, Shop shop) {
//        if (!cashier.hasRole(Role.CASHIER) && !cashier.hasRole(Role.ADMIN)) {
//            throw new RuntimeException("User must have CASHIER or ADMIN role");
//        }
//
//        if (!shop.hasCapacityForCashier()) {
//            throw new RuntimeException("Shop has reached maximum cashier capacity");
//        }
//
//        // Remove from previous shop if any
//        shopRepository.findByCashier(cashier)
//                .ifPresent(previousShop -> {
//                    previousShop.getAssignedCashiers().remove(cashier);
//                    shopRepository.save(previousShop);
//                });
//
//        cashier.setAssignedShop(shop);
//        shop.getAssignedCashiers().add(cashier);
//        shopRepository.save(shop);
//
//        log.info("Assigned cashier {} to shop {}", cashier.getUsername(), shop.getName());
//    }

    @Transactional
    public void assignCashierToShop(Cashier cashier, Shop shop) {
        // Check if cashier has appropriate role
        if (cashier.getRole() != CashierRole.CASHIER &&
                cashier.getRole() != CashierRole.SUPERVISOR &&
                cashier.getRole() != CashierRole.MANAGER &&
                cashier.getRole() != CashierRole.ADMIN) {
            throw new RuntimeException("User must have CASHIER, SUPERVISOR, MANAGER, or ADMIN role");
        }

        if (!shop.hasCapacityForCashier()) {
            throw new RuntimeException("Shop has reached maximum cashier capacity");
        }

        // Remove from previous shop if any
        shopRepository.findByCashier(cashier)
                .ifPresent(previousShop -> {
                    previousShop.getAssignedCashiers().remove(cashier);
                    shopRepository.save(previousShop);
                });

        cashier.setAssignedShop(shop);
        shop.getAssignedCashiers().add(cashier);
        shopRepository.save(shop);

        log.info("Assigned cashier {} to shop {}", cashier.getUsername(), shop.getName());
    }

    @Transactional
    public void removeCashierFromShop(Cashier cashier) {
        Shop currentShop = cashier.getAssignedShop();
        if (currentShop != null) {
            currentShop.getAssignedCashiers().remove(cashier);
            cashier.setAssignedShop(null);
            shopRepository.save(currentShop);
            log.info("Removed cashier {} from shop {}", cashier.getUsername(), currentShop.getName());
        }
    }

    @Transactional
    public void setShopManager(Shop shop, Cashier manager) {
        validateManager(manager);
        shop.setManager(manager);
        shopRepository.save(shop);
        log.info("Set {} as manager of shop {}", manager.getUsername(), shop.getName());
    }

    @Transactional
    public void addShopManager(Shop shop, Cashier cashier) {
        validateManager(cashier);
        shop.getManagedByCashiers().add(cashier);
        shopRepository.save(shop);
        log.info("Added {} as additional manager of shop {}", cashier.getUsername(), shop.getName());
    }

    @Transactional
    public void removeShopManager(Shop shop, Cashier cashier) {
        shop.getManagedByCashiers().remove(cashier);
        shopRepository.save(shop);
        log.info("Removed {} as manager of shop {}", cashier.getUsername(), shop.getName());
    }

    private void validateManager(Cashier cashier) {
        if (cashier.getRole() != CashierRole.MANAGER &&
                cashier.getRole() != CashierRole.SUPERVISOR &&
                cashier.getRole() != CashierRole.ADMIN) {
            throw new RuntimeException("Cashier must have MANAGER, SUPERVISOR, or ADMIN role to manage shops");
        }
    }

    public void deactivateShop(Long shopId) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new RuntimeException("Shop not found: " + shopId));

        if (shop.isWarehouse()) {
            throw new RuntimeException("Cannot deactivate warehouse");
        }

        // Check if shop has pending transfers
        long activeTransfers = transferRepository.countActiveTransfersFromShop(shopId);
        if (activeTransfers > 0) {
            throw new RuntimeException("Cannot deactivate shop with active transfers");
        }

        shop.setActive(false);
        shopRepository.save(shop);
        log.info("Deactivated shop: {}", shop.getName());
    }

    public long countActiveShopsByType(ShopType type) {
        return shopRepository.countActiveByType(type);
    }

    private final InventoryTransferRepository transferRepository;
}