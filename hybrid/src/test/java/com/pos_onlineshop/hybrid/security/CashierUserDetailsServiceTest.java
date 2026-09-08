package com.pos_onlineshop.hybrid.security;

import com.pos_onlineshop.hybrid.cashier.Cashier;
import com.pos_onlineshop.hybrid.cashier.CashierRepository;
import com.pos_onlineshop.hybrid.enums.CashierRole;
import com.pos_onlineshop.hybrid.enums.Permission;
import com.pos_onlineshop.hybrid.services.CashierService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CashierUserDetailsServiceTest {

    @Mock private CashierRepository cashierRepository;
    @Mock private CashierService cashierService;

    private CashierUserDetailsService service;

    @BeforeEach
    void setUp() {
        service = new CashierUserDetailsService(cashierRepository, cashierService);
    }

    private Cashier cashier(String username, CashierRole role, boolean active) {
        return Cashier.builder().id(1L).employeeId("EMP1").username(username).password("hashed")
                .firstName("A").lastName("B").email("a@b.com").role(role).active(active).build();
    }

    @Test
    void plainCashierGetsOnlyTheCashierRole() {
        Cashier cashier = cashier("cashier1", CashierRole.CASHIER, true);
        lenient().when(cashierService.getEffectivePermissions(cashier)).thenReturn(Set.of(Permission.PROCESS_SALE));

        List<GrantedAuthority> authorities = service.authoritiesFor(cashier);
        Set<String> names = authorities.stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());

        assertTrue(names.contains("ROLE_CASHIER"));
        assertFalse(names.contains("ROLE_SUPERVISOR"));
        assertFalse(names.contains("ROLE_MANAGER"));
        assertFalse(names.contains("ROLE_ADMIN"));
        assertTrue(names.contains("PROCESS_SALE"));
    }

    @Test
    void adminCashierGetsTheFullCumulativeRoleHierarchy() {
        Cashier cashier = cashier("admin1", CashierRole.ADMIN, true);
        lenient().when(cashierService.getEffectivePermissions(cashier)).thenReturn(Set.of());

        List<GrantedAuthority> authorities = service.authoritiesFor(cashier);
        Set<String> names = authorities.stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());

        assertTrue(names.contains("ROLE_CASHIER"));
        assertTrue(names.contains("ROLE_SUPERVISOR"));
        assertTrue(names.contains("ROLE_MANAGER"));
        assertTrue(names.contains("ROLE_ADMIN"));
    }

    @Test
    void managerCashierGetsManagerAndBelowButNotAdmin() {
        Cashier cashier = cashier("mgr1", CashierRole.MANAGER, true);
        lenient().when(cashierService.getEffectivePermissions(cashier)).thenReturn(Set.of());

        Set<String> names = service.authoritiesFor(cashier).stream()
                .map(GrantedAuthority::getAuthority).collect(Collectors.toSet());

        assertTrue(names.contains("ROLE_CASHIER"));
        assertTrue(names.contains("ROLE_SUPERVISOR"));
        assertTrue(names.contains("ROLE_MANAGER"));
        assertFalse(names.contains("ROLE_ADMIN"));
    }

    @Test
    void loadUserByUsernameBuildsUserDetailsFromTheCashierRecord() {
        Cashier cashier = cashier("cashier2", CashierRole.CASHIER, true);
        when(cashierRepository.findByUsername("cashier2")).thenReturn(Optional.of(cashier));
        lenient().when(cashierService.getEffectivePermissions(cashier)).thenReturn(Set.of());

        UserDetails details = service.loadUserByUsername("cashier2");

        assertEquals("cashier2", details.getUsername());
        assertEquals("hashed", details.getPassword());
        assertTrue(details.isEnabled());
    }

    @Test
    void loadUserByUsernameReflectsInactiveCashierAsDisabled() {
        Cashier cashier = cashier("cashier3", CashierRole.CASHIER, false);
        when(cashierRepository.findByUsername("cashier3")).thenReturn(Optional.of(cashier));
        lenient().when(cashierService.getEffectivePermissions(cashier)).thenReturn(Set.of());

        UserDetails details = service.loadUserByUsername("cashier3");

        assertFalse(details.isEnabled());
    }

    @Test
    void loadUserByUsernameThrowsForAnUnknownUsername() {
        when(cashierRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, () -> service.loadUserByUsername("ghost"));
    }
}
