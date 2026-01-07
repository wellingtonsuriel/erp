package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.cashier.Cashier;
import com.pos_onlineshop.hybrid.cashier.CashierRepository;
import com.pos_onlineshop.hybrid.cashierPermission.CashierPermission;
import com.pos_onlineshop.hybrid.cashierPermission.CashierPermissionRepository;
import com.pos_onlineshop.hybrid.cashierSessions.CashierSession;
import com.pos_onlineshop.hybrid.cashierSessions.CashierSessionRepository;
import com.pos_onlineshop.hybrid.enums.CashierRole;
import com.pos_onlineshop.hybrid.enums.Permission;
import com.pos_onlineshop.hybrid.enums.SessionStatus;
import com.pos_onlineshop.hybrid.shop.Shop;
import java.util.stream.Collectors;
import java.util.Map;
import java.util.HashMap;

import com.pos_onlineshop.hybrid.shop.ShopRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CashierService {

    private final CashierRepository cashierRepository;
    private final CashierSessionRepository sessionRepository;
    private final CashierPermissionRepository permissionRepository;
    private final PasswordEncoder passwordEncoder;
    private final ShopRepository shopRepository;

    public Cashier createCashier(Cashier cashier, String plainPassword) {
        if (cashierRepository.existsByUsername(cashier.getUsername())) {
            throw new RuntimeException("Username already exists");
        }
        if (cashierRepository.existsByEmail(cashier.getEmail())) {
            throw new RuntimeException("Email already exists");
        }

        // Auto-generate unique employee ID if not provided
        if (cashier.getEmployeeId() == null || cashier.getEmployeeId().trim().isEmpty()) {
            cashier.setEmployeeId(generateUniqueEmployeeId());
        } else {
            // If employee ID is provided, validate it's unique
            if (cashierRepository.existsByEmployeeId(cashier.getEmployeeId())) {
                throw new RuntimeException("Employee ID already exists");
            }
        }

        cashier.setPassword(passwordEncoder.encode(plainPassword));
        if (cashier.getPinCode() != null) {
            cashier.setPinCode(passwordEncoder.encode(cashier.getPinCode()));
        }

        Cashier saved = cashierRepository.save(cashier);

        // Grant default permissions based on role
        grantDefaultPermissions(saved);

        log.info("Created new cashier: {} ({}) with Employee ID: {}",
                saved.getFullName(), saved.getUsername(), saved.getEmployeeId());
        return saved;
    }

    private String generateUniqueEmployeeId() {
        try {
            Optional<String> maxEmployeeIdOpt = cashierRepository.findMaxEmployeeId();

            int nextNumber = 1;

            if (maxEmployeeIdOpt.isPresent() && maxEmployeeIdOpt.get() != null) {
                String maxEmployeeId = maxEmployeeIdOpt.get();

                if (maxEmployeeId.startsWith("EMP") && maxEmployeeId.length() >= 6) {
                    try {
                        String numberPart = maxEmployeeId.substring(3);
                        nextNumber = Integer.parseInt(numberPart) + 1;
                    } catch (NumberFormatException e) {
                        log.warn("Invalid max employee ID format: {}", maxEmployeeId);
                        nextNumber = 1;
                    }
                }
            }

            return String.format("EMP%03d", nextNumber);

        } catch (Exception e) {
            log.error("Error with MAX query, using fallback: {}", e.getMessage());
            return generateUniqueEmployeeIdFallback();
        }
    }

    private String generateUniqueEmployeeIdFallback() {
        String employeeId;
        int counter = 1;

        do {
            employeeId = String.format("EMP%03d", counter);
            counter++;

            // Safety check to prevent infinite loop
            if (counter > 999999) {
                throw new RuntimeException("Unable to generate unique employee ID - too many employees");
            }

        } while (cashierRepository.existsByEmployeeId(employeeId));

        log.debug("Generated unique employee ID using fallback method: {}", employeeId);
        return employeeId;
    }




    public Optional<Cashier> authenticateCashier(String username, String password) {
        return cashierRepository.findByUsername(username)
                .filter(cashier -> cashier.isActive() &&
                        passwordEncoder.matches(password, cashier.getPassword()));
    }

    public Optional<Cashier> authenticateByPin(String employeeId, String pin) {
        return cashierRepository.findByEmployeeId(employeeId)
                .filter(cashier -> cashier.isActive() &&
                        cashier.getPinCode() != null &&
                        passwordEncoder.matches(pin, cashier.getPinCode()));
    }

    public CashierSession startSession(Cashier cashier, Shop shop, String terminalId,
                                       BigDecimal openingCash) {
        // Check if cashier already has an active session
        Optional<CashierSession> activeSession = sessionRepository
                .findByCashierAndStatus(cashier, SessionStatus.ACTIVE);
        if (activeSession.isPresent()) {
            throw new RuntimeException("Cashier already has an active session");
        }

        // Verify cashier can access this shop
        if (!cashier.canAccessShop(shop) && !cashier.isManager()) {
            throw new RuntimeException("Cashier not authorized for this shop");
        }

        CashierSession session = CashierSession.builder()
                .cashier(cashier)
                .shop(shop)
                .terminalId(terminalId)
                .sessionStart(LocalDateTime.now())
                .openingCash(openingCash)
                .expectedCash(openingCash)
                .status(SessionStatus.ACTIVE)
                .build();

        cashier.setLastLogin(LocalDateTime.now());
        cashierRepository.save(cashier);

        return sessionRepository.save(session);
    }

    @Transactional
    public CashierSession endSession(Long sessionId, BigDecimal closingCash, String notes) {
        CashierSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found"));

        if (!session.isActive()) {
            throw new RuntimeException("Session is not active");
        }

        session.endSession(closingCash);
        session.setNotes(notes);

        return sessionRepository.save(session);
    }

    @Transactional
    public void recordSale(CashierSession session, BigDecimal saleAmount) {
        if (!session.isActive()) {
            throw new RuntimeException("Cannot record sale on inactive session");
        }

        session.setTotalSales(session.getTotalSales().add(saleAmount));
        session.setTransactionCount(session.getTransactionCount() + 1);
        session.setExpectedCash(session.getExpectedCash().add(saleAmount));

        sessionRepository.save(session);
    }

    public List<Cashier> findByShop(Shop shop) {
        return cashierRepository.findByAssignedShop(shop);
    }

    public List<Cashier> findActiveByShop(Long shopId) {
        return cashierRepository.findActiveByShopId(shopId);
    }

    public Optional<Cashier> findById(Long id) {
        return cashierRepository.findById(id);
    }

    public Optional<Cashier> findByUsername(String username) {
        return cashierRepository.findByUsername(username);
    }

    public Optional<Cashier> findByEmployeeId(String employeeId) {
        return cashierRepository.findByEmployeeId(employeeId);
    }

    public List<Cashier> findAllActive() {
        return cashierRepository.findByActiveTrue();
    }

    public Cashier updateCashier(Long id, Cashier cashierDetails) {
        return cashierRepository.findById(id)
                .map(cashier -> {
                    cashier.setFirstName(cashierDetails.getFirstName());
                    cashier.setLastName(cashierDetails.getLastName());
                    cashier.setEmail(cashierDetails.getEmail());
                    cashier.setPhoneNumber(cashierDetails.getPhoneNumber());
                    cashier.setRole(cashierDetails.getRole());
                    return cashierRepository.save(cashier);
                })
                .orElseThrow(() -> new RuntimeException("Cashier not found"));
    }

    public void assignToShop(Cashier cashier, Shop shop) {
        cashier.setAssignedShop(shop);
        cashierRepository.save(cashier);
        log.info("Assigned cashier {} to shop {}", cashier.getFullName(), shop.getName());
    }

    public void deactivateCashier(Long cashierId) {
        Cashier cashier = cashierRepository.findById(cashierId)
                .orElseThrow(() -> new RuntimeException("Cashier not found"));

        // End any active sessions
        sessionRepository.findByCashierAndStatus(cashier, SessionStatus.ACTIVE)
                .ifPresent(session -> endSession(session.getId(), session.getExpectedCash(),
                        "Session ended due to cashier deactivation"));

        cashier.setActive(false);
        cashierRepository.save(cashier);
        log.info("Deactivated cashier: {}", cashier.getFullName());
    }

    // Permission management
    public void grantPermission(Cashier cashier, Permission permission, Cashier grantedBy) {
        if (permissionRepository.existsByCashierAndPermission(cashier, permission)) {
            return; // Already has permission
        }

        CashierPermission cp = CashierPermission.builder()
                .cashier(cashier)
                .permission(permission)
                .grantedBy(grantedBy)
                .build();

        permissionRepository.save(cp);
        log.info("Granted {} permission to {}", permission, cashier.getFullName());
    }

    public boolean hasPermission(Cashier cashier, Permission permission) {
        // Admins have all permissions
        if (cashier.getRole() == CashierRole.ADMIN) {
            return true;
        }

        // Check role-based permissions
        if (isPermissionGrantedByRole(cashier.getRole(), permission)) {
            return true;
        }

        // Check individually granted permissions
        return permissionRepository.existsByCashierAndPermission(cashier, permission);
    }

    public List<Permission> getCashierPermissions(Long cashierId) {
        return permissionRepository.findPermissionsByCashierId(cashierId);
    }

    private void grantDefaultPermissions(Cashier cashier) {
        Set<Permission> defaultPermissions = getDefaultPermissionsByRole(cashier.getRole());
        for (Permission permission : defaultPermissions) {
            grantPermission(cashier, permission, null);
        }
    }

    private Set<Permission> getDefaultPermissionsByRole(CashierRole role) {
        Set<Permission> permissions = new HashSet<>();

        // Basic cashier permissions
        permissions.add(Permission.PROCESS_SALE);
        permissions.add(Permission.OPEN_CASH_DRAWER);
        permissions.add(Permission.VIEW_INVENTORY);

        if (role == CashierRole.SUPERVISOR || role == CashierRole.MANAGER || role == CashierRole.ADMIN) {
            permissions.add(Permission.PROCESS_RETURN);
            permissions.add(Permission.APPLY_DISCOUNT);
            permissions.add(Permission.PERFORM_CASH_COUNT);
            permissions.add(Permission.VIEW_REPORTS);
        }

        if (role == CashierRole.MANAGER || role == CashierRole.ADMIN) {
            permissions.add(Permission.TRANSFER_INVENTORY);
            permissions.add(Permission.ADJUST_INVENTORY);
            permissions.add(Permission.MANAGE_CASHIERS);
            permissions.add(Permission.OVERRIDE_PRICE);
            permissions.add(Permission.VOID_TRANSACTION);
        }

        if (role == CashierRole.ADMIN) {
            permissions.add(Permission.ACCESS_BACK_OFFICE);
            permissions.add(Permission.MODIFY_SETTINGS);
            permissions.add(Permission.ADJUST_CASH);
        }

        return permissions;
    }

    private boolean isPermissionGrantedByRole(CashierRole role, Permission permission) {
        return getDefaultPermissionsByRole(role).contains(permission);
    }
    public Optional<CashierSession> getActiveSession(Long cashierId) {
        return sessionRepository.findActiveByCashierId(cashierId);
    }

    public List<CashierSession> getSessionHistory(Cashier cashier,
                                                  LocalDateTime startDate,
                                                  LocalDateTime endDate) {
        return sessionRepository.findByDateRange(startDate, endDate).stream()
                .filter(session -> session.getCashier().equals(cashier))
                .collect(Collectors.toList());
    }

    // Add these methods to CashierService.java

    /**
     * Get all active sessions across all cashiers
     */
    @Transactional(readOnly = true)
    public List<CashierSession> getAllActiveSessions() {
        return sessionRepository.findByStatus(SessionStatus.ACTIVE);
    }

    /**
     * Get all sessions for a date range
     */
    @Transactional(readOnly = true)
    public List<CashierSession> getSessionsByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        return sessionRepository.findByDateRange(startDate, endDate);
    }

    /**
     * Get session statistics for a cashier
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getCashierSessionStats(Long cashierId, LocalDateTime startDate, LocalDateTime endDate) {
        Cashier cashier = cashierRepository.findById(cashierId)
                .orElseThrow(() -> new RuntimeException("Cashier not found"));

        List<CashierSession> sessions = getSessionHistory(cashier, startDate, endDate);

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalSessions", sessions.size());
        stats.put("totalSales", sessions.stream()
                .map(CashierSession::getTotalSales)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        stats.put("totalTransactions", sessions.stream()
                .mapToInt(CashierSession::getTransactionCount)
                .sum());
        stats.put("averageSessionDuration", calculateAverageSessionDuration(sessions));
        stats.put("averageSalesPerSession", calculateAverageSalesPerSession(sessions));

        return stats;
    }

    /**
     * Calculate average session duration in minutes
     */
    private double calculateAverageSessionDuration(List<CashierSession> sessions) {
        if (sessions.isEmpty()) return 0.0;

        double totalMinutes = sessions.stream()
                .filter(session -> session.getSessionEnd() != null)
                .mapToLong(session -> java.time.Duration.between(
                        session.getSessionStart(),
                        session.getSessionEnd()).toMinutes())
                .average()
                .orElse(0.0);

        return totalMinutes;
    }

    /**
     * Calculate average sales per session
     */
    private BigDecimal calculateAverageSalesPerSession(List<CashierSession> sessions) {
        if (sessions.isEmpty()) return BigDecimal.ZERO;

        BigDecimal totalSales = sessions.stream()
                .map(CashierSession::getTotalSales)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return totalSales.divide(BigDecimal.valueOf(sessions.size()), 2, BigDecimal.ROUND_HALF_UP);
    }

    /**
     * Get cashiers by role
     */
    @Transactional(readOnly = true)
    public List<Cashier> findByRole(CashierRole role) {
        return cashierRepository.findByRole(role);
    }

    /**
     * Update cashier password
     */
    @Transactional
    public void updatePassword(Long cashierId, String newPassword) {
        Cashier cashier = cashierRepository.findById(cashierId)
                .orElseThrow(() -> new RuntimeException("Cashier not found"));

        cashier.setPassword(passwordEncoder.encode(newPassword));
        cashierRepository.save(cashier);
        log.info("Updated password for cashier: {}", cashier.getUsername());
    }

    /**
     * Update cashier PIN
     */
    @Transactional
    public void updatePin(Long cashierId, String newPin) {
        Cashier cashier = cashierRepository.findById(cashierId)
                .orElseThrow(() -> new RuntimeException("Cashier not found"));

        cashier.setPinCode(passwordEncoder.encode(newPin));
        cashierRepository.save(cashier);
        log.info("Updated PIN for cashier: {}", cashier.getUsername());
    }

    /**
     * Check if cashier can access shop
     */
    public boolean canAccessShop(Long cashierId, Long shopId) {
        Cashier cashier = cashierRepository.findById(cashierId)
                .orElseThrow(() -> new RuntimeException("Cashier not found"));

        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new RuntimeException("Shop not found"));

        return cashier.canAccessShop(shop) || cashier.isManager();
    }

    /**
     * Get top performing cashiers by sales
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getTopPerformingCashiers(LocalDateTime startDate, LocalDateTime endDate, int limit) {
        List<CashierSession> sessions = sessionRepository.findByDateRange(startDate, endDate);

        Map<Long, BigDecimal> cashierSales = sessions.stream()
                .collect(Collectors.groupingBy(
                        session -> session.getCashier().getId(),
                        Collectors.reducing(BigDecimal.ZERO, CashierSession::getTotalSales, BigDecimal::add)
                ));

        return cashierSales.entrySet().stream()
                .sorted(Map.Entry.<Long, BigDecimal>comparingByValue().reversed())
                .limit(limit)
                .map(entry -> {
                    Cashier cashier = cashierRepository.findById(entry.getKey()).orElse(null);
                    Map<String, Object> result = new HashMap<>();
                    result.put("cashier", cashier);
                    result.put("totalSales", entry.getValue());
                    return result;
                })
                .collect(Collectors.toList());
    }



    /**
     * Find all cashiers assigned to a shop
     */
    @Transactional(readOnly = true)
    public List<Cashier> findCashiersByShopId(Long shopId) {
        return cashierRepository.findByAssignedShop_Id(shopId);
    }

    /**
     * Find active cashiers in a shop
     */
    @Transactional(readOnly = true)
    public List<Cashier> findActiveCashiersByShopId(Long shopId) {
        return cashierRepository.findActiveCashiersByShopId(shopId);
    }

    /**
     * Find cashiers by shop and role
     */
    @Transactional(readOnly = true)
    public List<Cashier> findCashiersByShopIdAndRole(Long shopId, CashierRole role) {
        return cashierRepository.findByShopIdAndRole(shopId, role);
    }

    /**
     * Find shop managers
     */
    @Transactional(readOnly = true)
    public List<Cashier> findManagersByShopId(Long shopId) {
        return cashierRepository.findManagersByShopId(shopId);
    }

    /**
     * Get cashier count for a shop
     */
    @Transactional(readOnly = true)
    public long getCashierCountByShopId(Long shopId) {
        return cashierRepository.countActiveCashiersByShopId(shopId);
    }

    /**
     * Check if shop has available cashier capacity
     */
    @Transactional(readOnly = true)
    public boolean hasAvailableCashierCapacity(Long shopId, int maxCashiers) {
        long currentCount = getCashierCountByShopId(shopId);
        return currentCount < maxCashiers;
    }

    public boolean validateTransferInitiator(Long shopId, Long cashierId) {
        List<Cashier> shopCashiers = findCashiersByShopId(shopId);
        return shopCashiers.stream().anyMatch(c -> c.getId().equals(cashierId));
    }

// Add imports to CashierService.java

}