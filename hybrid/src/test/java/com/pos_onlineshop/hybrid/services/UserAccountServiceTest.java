package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.enums.AccountingPermission;
import com.pos_onlineshop.hybrid.enums.Role;
import com.pos_onlineshop.hybrid.userAccount.UserAccount;
import com.pos_onlineshop.hybrid.userAccount.UserAccountRepository;
import com.pos_onlineshop.hybrid.userAccountPermission.UserAccountPermission;
import com.pos_onlineshop.hybrid.userAccountPermission.UserAccountPermissionRepository;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAccountServiceTest {

    @Mock private UserAccountRepository userRepository;
    @Mock private UserAccountPermissionRepository userAccountPermissionRepository;
    @Mock private CartService cartService;

    private UserAccountService service;

    @BeforeEach
    void setUp() {
        service = new UserAccountService(userRepository, userAccountPermissionRepository, null, cartService);
    }

    @Test
    void loadUserByUsernameBuildsRoleAuthoritiesFromEachRole() {
        UserAccount account = UserAccount.builder().id(1L).username("clerk1").password("hashed")
                .enabled(true).roles(Set.of(Role.ADMIN, Role.CASHIER)).build();
        when(userRepository.findByUsername("clerk1")).thenReturn(Optional.of(account));
        when(userAccountPermissionRepository.findByUserAccount(account)).thenReturn(List.of());

        UserDetails details = service.loadUserByUsername("clerk1");

        Set<String> authorities = details.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
        assertTrue(authorities.contains("ROLE_ADMIN"));
        assertTrue(authorities.contains("ROLE_CASHIER"));
    }

    @Test
    void loadUserByUsernameAddsUnprefixedAuthorityPerGrantedAccountingPermission() {
        UserAccount account = UserAccount.builder().id(1L).username("accountant1").password("hashed")
                .enabled(true).roles(Set.of(Role.USER)).build();
        UserAccountPermission glPost = UserAccountPermission.builder().permission(AccountingPermission.GL_POST).userAccount(account).build();
        UserAccountPermission apPay = UserAccountPermission.builder().permission(AccountingPermission.AP_PAY).userAccount(account).build();
        when(userRepository.findByUsername("accountant1")).thenReturn(Optional.of(account));
        when(userAccountPermissionRepository.findByUserAccount(account)).thenReturn(List.of(glPost, apPay));

        UserDetails details = service.loadUserByUsername("accountant1");

        Set<String> authorities = details.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
        assertTrue(authorities.contains("GL_POST"));
        assertTrue(authorities.contains("AP_PAY"));
        assertTrue(authorities.contains("ROLE_USER"));
        // Permission authorities are never ROLE_-prefixed
        assertFalse(authorities.contains("ROLE_GL_POST"));
    }

    @Test
    void loadUserByUsernameThrowsForAnUnknownUsername() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());
        assertThrows(UsernameNotFoundException.class, () -> service.loadUserByUsername("ghost"));
    }

    @Test
    void loadUserByUsernameReflectsDisabledFlag() {
        UserAccount account = UserAccount.builder().id(1L).username("disabled1").password("hashed")
                .enabled(false).roles(Set.of(Role.USER)).build();
        when(userRepository.findByUsername("disabled1")).thenReturn(Optional.of(account));
        when(userAccountPermissionRepository.findByUserAccount(account)).thenReturn(List.of());

        UserDetails details = service.loadUserByUsername("disabled1");

        assertFalse(details.isEnabled());
    }
}
