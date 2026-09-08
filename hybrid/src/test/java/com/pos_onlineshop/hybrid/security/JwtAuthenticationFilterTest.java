package com.pos_onlineshop.hybrid.security;

import com.pos_onlineshop.hybrid.services.UserAccountService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Covers the fallback that lets a Cashier-issued JWT authenticate successfully even though
 * UserAccountService (tried first) has never heard of that username - see the class comment
 * on JwtAuthenticationFilter and CashierUserDetailsService for why two disjoint principal
 * namespaces share this one filter.
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock private JwtService jwtService;
    @Mock private UserAccountService userAccountService;
    @Mock private CashierUserDetailsService cashierUserDetailsService;
    @Mock private FilterChain filterChain;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtService, userAccountService, cashierUserDetailsService);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest requestWithBearer(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    @Test
    void authenticatesAsACashierWhenUserAccountLookupFindsNoSuchUsername() throws Exception {
        UserDetails cashierDetails = new User("cashier1", "hashed", true, true, true, true,
                List.of(new SimpleGrantedAuthority("ROLE_CASHIER")));
        when(jwtService.extractUsername("cashier.jwt")).thenReturn("cashier1");
        when(userAccountService.loadUserByUsername("cashier1"))
                .thenThrow(new UsernameNotFoundException("no such user account"));
        when(cashierUserDetailsService.loadUserByUsername("cashier1")).thenReturn(cashierDetails);
        when(jwtService.isTokenValid("cashier.jwt", cashierDetails)).thenReturn(true);

        filter.doFilter(requestWithBearer("cashier.jwt"), new MockHttpServletResponse(), filterChain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("cashier1", SecurityContextHolder.getContext().getAuthentication().getName());
        List<String> authorities = SecurityContextHolder.getContext().getAuthentication().getAuthorities()
                .stream().map(GrantedAuthority::getAuthority).toList();
        assertTrue(authorities.contains("ROLE_CASHIER"));
        verify(filterChain).doFilter(any(), any());
    }

    @Test
    void authenticatesAsAUserAccountWhenOneExistsWithoutEverCallingCashierLookup() throws Exception {
        UserDetails userAccountDetails = new User("admin1", "hashed", true, true, true, true,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        when(jwtService.extractUsername("user.jwt")).thenReturn("admin1");
        when(userAccountService.loadUserByUsername("admin1")).thenReturn(userAccountDetails);
        when(jwtService.isTokenValid("user.jwt", userAccountDetails)).thenReturn(true);

        filter.doFilter(requestWithBearer("user.jwt"), new MockHttpServletResponse(), filterChain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        verify(cashierUserDetailsService, never()).loadUserByUsername(any());
    }

    @Test
    void leavesSecurityContextEmptyWhenNeitherPrincipalTypeMatches() throws Exception {
        when(jwtService.extractUsername("ghost.jwt")).thenReturn("ghost");
        when(userAccountService.loadUserByUsername("ghost")).thenThrow(new UsernameNotFoundException("no user"));
        when(cashierUserDetailsService.loadUserByUsername("ghost")).thenThrow(new UsernameNotFoundException("no cashier"));

        filter.doFilter(requestWithBearer("ghost.jwt"), new MockHttpServletResponse(), filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(any(), any());
    }

    @Test
    void leavesSecurityContextEmptyWhenNoAuthorizationHeaderIsSent() throws Exception {
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(jwtService);
    }
}
