package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.cashier.Cashier;
import com.pos_onlineshop.hybrid.cashierSessions.CashierSession;
import com.pos_onlineshop.hybrid.dtos.*;
import com.pos_onlineshop.hybrid.enums.CashierRole;
import com.pos_onlineshop.hybrid.enums.Permission;
import com.pos_onlineshop.hybrid.services.CashierService;
import com.pos_onlineshop.hybrid.services.ShopService;
import com.pos_onlineshop.hybrid.shop.Shop;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/cashiers")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class CashierController {

    private final CashierService cashierService;
    private final ShopService shopService;

    @PostMapping("/register")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Cashier> registerCashier(@RequestBody RegisterCashierRequest request) {
        try {
            Cashier cashier = Cashier.builder()
                    .employeeId(request.getEmployeeId())
                    .username(request.getUsername())
                    .firstName(request.getFirstName())
                    .lastName(request.getLastName())
                    .email(request.getEmail())
                    .phoneNumber(request.getPhoneNumber())
                    .role(request.getRole())
                    .hireDate(LocalDateTime.now())
                    .pinCode(request.getPinCode())
                    .build();

            Cashier created = cashierService.createCashier(cashier, request.getPassword());
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (RuntimeException e) {
            log.error("Error registering cashier", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<List<Cashier>> getAllCashiers() {
        List<Cashier> cashiers = cashierService.findAllActive();
        return ResponseEntity.ok(cashiers);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Cashier> getCashierById(@PathVariable Long id) {
        return cashierService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/username/{username}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Cashier> getCashierByUsername(@PathVariable String username) {
        return cashierService.findByUsername(username)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/employee/{employeeId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Cashier> getCashierByEmployeeId(@PathVariable String employeeId) {
        return cashierService.findByEmployeeId(employeeId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/shop/{shopId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<List<Cashier>> getCashiersByShop(@PathVariable Long shopId) {
        List<Cashier> cashiers = cashierService.findActiveByShop(shopId);
        return ResponseEntity.ok(cashiers);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Cashier> updateCashier(@PathVariable Long id, @RequestBody CashierUpdateRequest request) {
        try {
            Cashier cashierDetails = Cashier.builder()
                    .firstName(request.getFirstName())
                    .lastName(request.getLastName())
                    .email(request.getEmail())
                    .phoneNumber(request.getPhoneNumber())
                    .role(request.getRole())
                    .build();

            Cashier updated = cashierService.updateCashier(id, cashierDetails);
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            log.error("Error updating cashier", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deactivateCashier(@PathVariable Long id) {
        try {
            cashierService.deactivateCashier(id);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            log.error("Error deactivating cashier", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/{id}/assign-shop")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Void> assignCashierToShop(@PathVariable Long id, @RequestBody AssignShopRequest request) {
        try {
            Cashier cashier = cashierService.findById(id)
                    .orElseThrow(() -> new RuntimeException("Cashier not found"));

            Shop shop = shopService.findById(request.getShopId())
                    .orElseThrow(() -> new RuntimeException("Shop not found"));

            cashierService.assignToShop(cashier, shop);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            log.error("Error assigning cashier to shop", e);
            return ResponseEntity.badRequest().build();
        }
    }

    // Session Management
    @PostMapping("/sessions/start")
    @PreAuthorize("hasAnyRole('CASHIER', 'SUPERVISOR', 'MANAGER', 'ADMIN')")
    public ResponseEntity<CashierSession> startSession(@RequestBody StartSessionRequest request) {
        try {
            Cashier cashier = cashierService.findById(request.getCashierId())
                    .orElseThrow(() -> new RuntimeException("Cashier not found"));

            Shop shop = shopService.findById(request.getShopId())
                    .orElseThrow(() -> new RuntimeException("Shop not found"));

            CashierSession session = cashierService.startSession(
                    cashier, shop, request.getTerminalId(), request.getOpeningCash());

            return ResponseEntity.ok(session);
        } catch (RuntimeException e) {
            log.error("Error starting cashier session", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/sessions/{sessionId}/end")
    @PreAuthorize("hasAnyRole('CASHIER', 'SUPERVISOR', 'MANAGER', 'ADMIN')")
    public ResponseEntity<CashierSession> endSession(
            @PathVariable Long sessionId,
            @RequestBody EndSessionRequest request) {
        try {
            CashierSession session = cashierService.endSession(
                    sessionId, request.getClosingCash(), request.getNotes());
            return ResponseEntity.ok(session);
        } catch (RuntimeException e) {
            log.error("Error ending cashier session", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/sessions/active")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'MANAGER', 'ADMIN')")
    public ResponseEntity<List<CashierSession>> getActiveSessions() {
        try {
            List<CashierSession> activeSessions = cashierService.getAllActiveSessions();
            return ResponseEntity.ok(activeSessions);
        } catch (Exception e) {
            log.error("Error retrieving active sessions", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/sessions/history/{cashierId}")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'MANAGER', 'ADMIN')")
    public ResponseEntity<List<CashierSession>> getCashierSessionHistory(
            @PathVariable Long cashierId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        try {
            Cashier cashier = cashierService.findById(cashierId)
                    .orElseThrow(() -> new RuntimeException("Cashier not found"));

            List<CashierSession> sessionHistory = cashierService.getSessionHistory(cashier, startDate, endDate);
            return ResponseEntity.ok(sessionHistory);
        } catch (Exception e) {
            log.error("Error retrieving session history", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{cashierId}/sessions/active")
    @PreAuthorize("hasAnyRole('CASHIER', 'SUPERVISOR', 'MANAGER', 'ADMIN')")
    public ResponseEntity<CashierSession> getActiveSession(@PathVariable Long cashierId) {
        Optional<CashierSession> activeSession = cashierService.getActiveSession(cashierId);
        return activeSession.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Permission Management
    @PostMapping("/{id}/permissions")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public ResponseEntity<Void> grantPermission(@PathVariable Long id, @RequestBody GrantPermissionRequest request) {
        try {
            Cashier cashier = cashierService.findById(id)
                    .orElseThrow(() -> new RuntimeException("Cashier not found"));

            // TODO: Get current user as grantedBy
            Cashier grantedBy = null; // Should be obtained from security context

            cashierService.grantPermission(cashier, request.getPermission(), grantedBy);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            log.error("Error granting permission", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{id}/permissions")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public ResponseEntity<List<Permission>> getCashierPermissions(@PathVariable Long id) {
        List<Permission> permissions = cashierService.getCashierPermissions(id);
        return ResponseEntity.ok(permissions);
    }

    @GetMapping("/{id}/permissions/check")
    @PreAuthorize("hasAnyRole('CASHIER', 'SUPERVISOR', 'MANAGER', 'ADMIN')")
    public ResponseEntity<Map<String, Boolean>> checkPermission(
            @PathVariable Long id,
            @RequestParam Permission permission) {
        try {
            Cashier cashier = cashierService.findById(id)
                    .orElseThrow(() -> new RuntimeException("Cashier not found"));

            boolean hasPermission = cashierService.hasPermission(cashier, permission);
            Map<String, Boolean> result = Map.of("hasPermission", hasPermission);
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            log.error("Error checking permission", e);
            return ResponseEntity.badRequest().build();
        }
    }

    // Authentication
    @PostMapping("/authenticate")
    public ResponseEntity<Map<String, Object>> authenticateCashier(@RequestBody AuthenticationRequest request) {
        try {
            Optional<Cashier> cashierOpt = cashierService.authenticateCashier(
                    request.getUsername(), request.getPassword());

            if (cashierOpt.isPresent()) {
                Cashier cashier = cashierOpt.get();
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("cashier", cashier);
                response.put("permissions", cashierService.getCashierPermissions(cashier.getId()));
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("success", false, "message", "Invalid credentials"));
            }
        } catch (Exception e) {
            log.error("Error authenticating cashier", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("success", false, "message", "Authentication error"));
        }
    }

    @PostMapping("/authenticate/pin")
    public ResponseEntity<Map<String, Object>> authenticateByPin(@RequestBody PinAuthenticationRequest request) {
        try {
            Optional<Cashier> cashierOpt = cashierService.authenticateByPin(
                    request.getEmployeeId(), request.getPin());

            if (cashierOpt.isPresent()) {
                Cashier cashier = cashierOpt.get();
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("cashier", cashier);
                response.put("permissions", cashierService.getCashierPermissions(cashier.getId()));
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("success", false, "message", "Invalid PIN"));
            }
        } catch (Exception e) {
            log.error("Error authenticating cashier by PIN", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("success", false, "message", "Authentication error"));
        }
    }




    /**
     * Get active cashiers for a shop
     */
    @GetMapping("/shop/{shopId}/active")
    public ResponseEntity<List<Cashier>> getActiveCashiersByShop(@PathVariable Long shopId) {
        List<Cashier> cashiers = cashierService.findActiveCashiersByShopId(shopId);
        return ResponseEntity.ok(cashiers);
    }

    /**
     * Get cashiers by shop and role
     */
    @GetMapping("/shop/{shopId}/role/{role}")
    public ResponseEntity<List<Cashier>> getCashiersByShopAndRole(
            @PathVariable Long shopId,
            @PathVariable CashierRole role) {
        List<Cashier> cashiers = cashierService.findCashiersByShopIdAndRole(shopId, role);
        return ResponseEntity.ok(cashiers);
    }

    /**
     * Get managers for a shop
     */
    @GetMapping("/shop/{shopId}/managers")
    public ResponseEntity<List<Cashier>> getManagersByShop(@PathVariable Long shopId) {
        List<Cashier> managers = cashierService.findManagersByShopId(shopId);
        return ResponseEntity.ok(managers);
    }

    /**
     * Get cashier count for a shop
     */
    @GetMapping("/shop/{shopId}/count")
    public ResponseEntity<Long> getCashierCountByShop(@PathVariable Long shopId) {
        long count = cashierService.getCashierCountByShopId(shopId);
        return ResponseEntity.ok(count);
    }

    /**
     * Check if shop has cashier capacity
     */
    @GetMapping("/shop/{shopId}/capacity-available")
    public ResponseEntity<Boolean> checkCashierCapacity(
            @PathVariable Long shopId,
            @RequestParam int maxCashiers) {
        boolean hasCapacity = cashierService.hasAvailableCashierCapacity(shopId, maxCashiers);
        return ResponseEntity.ok(hasCapacity);
    }


}