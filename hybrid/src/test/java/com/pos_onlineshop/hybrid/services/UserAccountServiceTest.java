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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class UserAccountServiceTest {

    @Mock private UserAccountRepository userRepository;
    @Mock private UserAccountPermissionRepository userAccountPermissionRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private CartService cartService;

    private UserAccountService service;

    @BeforeEach
    void setUp() {
        service = new UserAccountService(userRepository, userAccountPermissionRepository, passwordEncoder, cartService);
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

    @Test
    void registerUserHashesThePasswordAndDefaultsToTheUserRole() {
        when(userRepository.existsByUsername("newbie")).thenReturn(false);
        when(userRepository.existsByEmail("newbie@example.com")).thenReturn(false);
        when(passwordEncoder.encode("plaintext")).thenReturn("$2a$10$hashed");
        when(userRepository.save(org.mockito.ArgumentMatchers.any(UserAccount.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UserAccount created = service.registerUser("newbie", "plaintext", "newbie@example.com");

        assertEquals("$2a$10$hashed", created.getPassword());
        assertNotEquals("plaintext", created.getPassword());
        assertEquals(Set.of(Role.USER), created.getRoles());
        assertTrue(created.isEnabled());
        verify(cartService).createCart(created);
    }

    @Test
    void registerUserRejectsADuplicateUsernameWithoutSavingAnything() {
        when(userRepository.existsByUsername("taken")).thenReturn(true);

        assertThrows(RuntimeException.class, () -> service.registerUser("taken", "pw", "e@example.com"));

        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void registerUserRejectsADuplicateEmailWithoutSavingAnything() {
        when(userRepository.existsByUsername("newbie")).thenReturn(false);
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        assertThrows(RuntimeException.class, () -> service.registerUser("newbie", "pw", "taken@example.com"));

        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void addRoleToUserGrantsAnAdditionalRoleWithoutRemovingTheExistingOne() {
        UserAccount account = UserAccount.builder().id(5L).username("promotable").password("hashed")
                .enabled(true).roles(new java.util.HashSet<>(Set.of(Role.USER))).build();
        when(userRepository.findById(5L)).thenReturn(Optional.of(account));
        when(userRepository.save(account)).thenReturn(account);

        service.addRoleToUser(5L, Role.ADMIN);

        ArgumentCaptor<UserAccount> savedCaptor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userRepository).save(savedCaptor.capture());
        assertEquals(Set.of(Role.USER, Role.ADMIN), savedCaptor.getValue().getRoles());
    }
}
