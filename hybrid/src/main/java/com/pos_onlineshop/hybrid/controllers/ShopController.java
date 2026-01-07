package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.cashier.Cashier;
import com.pos_onlineshop.hybrid.cashier.CashierRepository;
import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.currency.CurrencyRepository;
import com.pos_onlineshop.hybrid.dtos.ShopCreateRequest;
import com.pos_onlineshop.hybrid.dtos.ShopUpdateRequest;
import com.pos_onlineshop.hybrid.enums.ShopType;
import com.pos_onlineshop.hybrid.services.ShopService;
import com.pos_onlineshop.hybrid.shop.Shop;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/shops")
@RequiredArgsConstructor
@Slf4j
public class ShopController {

    private final ShopService shopService;
    private final CashierRepository cashierRepository;
    private final CurrencyRepository currencyRepository;

    /**
     * Create a new shop
     */
    @PostMapping
    public ResponseEntity<Shop> createShop(@RequestBody ShopCreateRequest request) {
        try {
            Shop shop = new Shop();
            shop.setName(request.getName());
            shop.setCode(request.getCode());
            shop.setAddress(request.getAddress());
            shop.setPhoneNumber(request.getPhoneNumber());
            shop.setEmail(request.getEmail());
            shop.setOpeningTime(request.getOpeningTime());
            shop.setClosingTime(request.getClosingTime());
            shop.setType(request.getType());
            shop.setActive(request.isActive());
            shop.setMaxCashiers(request.getMaxCashiers());
            shop.setStorageCapacity(request.getStorageCapacity());

            // Handle currency properly
            if (request.getDefaultCurrencyId() != null) {
                Optional<Currency> currency = currencyRepository.findById(request.getDefaultCurrencyId());
                if (currency.isPresent()) {
                    shop.setDefaultCurrency(currency.get());
                } else {
                    return ResponseEntity.badRequest().build();
                }
            }

            Shop createdShop = shopService.createShop(shop);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdShop);
        } catch (RuntimeException e) {
            log.error("Error creating shop", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get shop by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<Shop> getShopById(@PathVariable Long id) {
        Optional<Shop> shop = shopService.findById(id);
        return shop.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get shop by code
     */
    @GetMapping("/code/{code}")
    public ResponseEntity<Shop> getShopByCode(@PathVariable String code) {
        Optional<Shop> shop = shopService.findByCode(code);
        return shop.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get all shops
     */
    @GetMapping
    public ResponseEntity<List<Shop>> getAllShops() {
        List<Shop> shops = shopService.findAllShops();
        return ResponseEntity.ok(shops);
    }

    /**
     * Get all active shops
     */
    @GetMapping("/active")
    public ResponseEntity<List<Shop>> getActiveShops() {
        List<Shop> shops = shopService.findActiveShops();
        return ResponseEntity.ok(shops);
    }

    /**
     * Get shops by type
     */
    @GetMapping("/type/{type}")
    public ResponseEntity<List<Shop>> getShopsByType(@PathVariable ShopType type) {
        List<Shop> shops = shopService.findByType(type);
        return ResponseEntity.ok(shops);
    }

    /**
     * Get warehouse shop
     */
    @GetMapping("/warehouse")
    public ResponseEntity<Shop> getWarehouse() {
        Optional<Shop> warehouse = shopService.findWarehouse();
        return warehouse.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get shops managed by a cashier
     */
    @GetMapping("/managed-by/{cashierId}")
    public ResponseEntity<List<Shop>> getManagedShops(@PathVariable Long cashierId) {
        Optional<Cashier> cashier = cashierRepository.findById(cashierId);
        if (cashier.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        List<Shop> shops = shopService.findManagedShops(cashier.get());
        return ResponseEntity.ok(shops);
    }

    /**
     * Update shop
     */
    @PutMapping("/{id}")
    public ResponseEntity<Shop> updateShop(
            @PathVariable Long id,
            @RequestBody ShopUpdateRequest request) {

        try {
            Shop shopDetails = new Shop();
            shopDetails.setName(request.getName());
            shopDetails.setAddress(request.getAddress());
            shopDetails.setPhoneNumber(request.getPhoneNumber());
            shopDetails.setEmail(request.getEmail());
            shopDetails.setOpeningTime(request.getOpeningTime());
            shopDetails.setClosingTime(request.getClosingTime());
            shopDetails.setActive(request.isActive());
            shopDetails.setMaxCashiers(request.getMaxCashiers());
            shopDetails.setStorageCapacity(request.getStorageCapacity());

            // Handle currency properly
            if (request.getDefaultCurrencyId() != null) {
                Optional<Currency> currency = currencyRepository.findById(request.getDefaultCurrencyId());
                if (currency.isPresent()) {
                    shopDetails.setDefaultCurrency(currency.get());
                }
            }

            if (request.getManagerId() != null) {
                Optional<Cashier> manager = cashierRepository.findById(request.getManagerId());
                if (manager.isPresent()) {
                    shopDetails.setManager(manager.get());
                }
            }

            Shop updatedShop = shopService.updateShop(id, shopDetails);
            return ResponseEntity.ok(updatedShop);
        } catch (RuntimeException e) {
            log.error("Error updating shop", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Assign cashier to shop
     */
    @PostMapping("/{shopId}/assign-cashier/{cashierId}")
    public ResponseEntity<Void> assignCashierToShop(
            @PathVariable Long shopId,
            @PathVariable Long cashierId) {

        try {
            Optional<Shop> shop = shopService.findById(shopId);
            Optional<Cashier> cashier = cashierRepository.findById(cashierId);

            if (shop.isEmpty() || cashier.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            shopService.assignCashierToShop(cashier.get(), shop.get());
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            log.error("Error assigning cashier to shop", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Remove cashier from their assigned shop
     */
    @PostMapping("/remove-cashier/{cashierId}")
    public ResponseEntity<Void> removeCashierFromShop(@PathVariable Long cashierId) {
        try {
            Optional<Cashier> cashier = cashierRepository.findById(cashierId);
            if (cashier.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            shopService.removeCashierFromShop(cashier.get());
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            log.error("Error removing cashier from shop", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Set shop manager
     */
    @PostMapping("/{shopId}/set-manager/{managerId}")
    public ResponseEntity<Void> setShopManager(
            @PathVariable Long shopId,
            @PathVariable Long managerId) {

        try {
            Optional<Shop> shop = shopService.findById(shopId);
            Optional<Cashier> manager = cashierRepository.findById(managerId);

            if (shop.isEmpty() || manager.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            shopService.setShopManager(shop.get(), manager.get());
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            log.error("Error setting shop manager", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Add additional manager to shop
     */
    @PostMapping("/{shopId}/add-manager/{managerId}")
    public ResponseEntity<Void> addShopManager(
            @PathVariable Long shopId,
            @PathVariable Long managerId) {

        try {
            Optional<Shop> shop = shopService.findById(shopId);
            Optional<Cashier> manager = cashierRepository.findById(managerId);

            if (shop.isEmpty() || manager.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            shopService.addShopManager(shop.get(), manager.get());
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            log.error("Error adding shop manager", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Remove manager from shop
     */
    @PostMapping("/{shopId}/remove-manager/{managerId}")
    public ResponseEntity<Void> removeShopManager(
            @PathVariable Long shopId,
            @PathVariable Long managerId) {

        try {
            Optional<Shop> shop = shopService.findById(shopId);
            Optional<Cashier> manager = cashierRepository.findById(managerId);

            if (shop.isEmpty() || manager.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            shopService.removeShopManager(shop.get(), manager.get());
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            log.error("Error removing shop manager", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Deactivate shop
     */
    @PostMapping("/{shopId}/deactivate")
    public ResponseEntity<Void> deactivateShop(@PathVariable Long shopId) {
        try {
            shopService.deactivateShop(shopId);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            log.error("Error deactivating shop", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get count of active shops by type
     */
    @GetMapping("/count/type/{type}")
    public ResponseEntity<Long> getActiveShopsCountByType(@PathVariable ShopType type) {
        long count = shopService.countActiveShopsByType(type);
        return ResponseEntity.ok(count);
    }


}