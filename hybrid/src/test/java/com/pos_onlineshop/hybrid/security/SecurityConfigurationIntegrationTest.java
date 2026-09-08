package com.pos_onlineshop.hybrid.security;

import com.pos_onlineshop.hybrid.config.SecurityConfiguration;
import com.pos_onlineshop.hybrid.controllers.ManualJournalController;
import com.pos_onlineshop.hybrid.services.ManualJournalService;
import com.pos_onlineshop.hybrid.services.UserAccountService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A genuine Spring Security slice test - not a plain Mockito unit test that bypasses AOP method
 * security entirely - proving the audit's P0-1 fix (removing "/**" from the permitAll list) and
 * P1-4 fix (per-controller @PreAuthorize coverage) actually work together at the filter-chain
 * level: no token -> 401, a syntactically valid but unrecognized-principal token -> 401 (Spring
 * Security's default AuthenticationEntryPoint fires because .anyRequest().authenticated() is no
 * longer short-circuited by a blanket permitAll), a real principal without the required
 * authority -> 403, and a real principal with it -> 200. MySQL is unreachable in this
 * environment, so this uses @WebMvcTest (a web-layer slice, no JPA/DataSource) against
 * ManualJournalController rather than a full @SpringBootTest.
 */
@WebMvcTest(controllers = ManualJournalController.class)
@Import({SecurityConfiguration.class, JwtAuthenticationFilter.class})
@TestPropertySource(properties = {
        "jwt.secret=test-only-secret-at-least-32-bytes-long-for-hmac-sha",
        "jwt.expiration-ms=3600000"
})
class SecurityConfigurationIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;

    @MockitoBean private ManualJournalService manualJournalService;
    @MockitoBean private UserAccountService userAccountService;
    @MockitoBean private CashierUserDetailsService cashierUserDetailsService;
    @MockitoBean private AuthenticationProvider authenticationProvider;

    @TestConfiguration
    static class RealJwtServiceConfig {
        @Bean
        JwtService jwtService(org.springframework.core.env.Environment env) {
            return new JwtService(env.getProperty("jwt.secret"),
                    Long.parseLong(env.getProperty("jwt.expiration-ms")));
        }
    }

    private String tokenFor(String username, GrantedAuthority... authorities) {
        UserDetails details = new User(username, "hashed", true, true, true, true, List.of(authorities));
        return jwtService.generateToken(details);
    }

    @Test
    void protectedEndpointWithNoTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/manual-journals"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointWithATokenForAnUnknownPrincipalReturns401() throws Exception {
        // Signature-valid JWT, but neither UserAccountService nor CashierUserDetailsService
        // recognizes the username - JwtAuthenticationFilter leaves the SecurityContext empty
        // (see its class comment), so .anyRequest().authenticated() correctly rejects it.
        String token = tokenFor("ghost");
        when(userAccountService.loadUserByUsername("ghost")).thenThrow(new UsernameNotFoundException("no such user"));
        when(cashierUserDetailsService.loadUserByUsername("ghost")).thenThrow(new UsernameNotFoundException("no such cashier"));

        mockMvc.perform(get("/api/manual-journals").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedPrincipalWithoutTheRequiredAuthorityReturns403() throws Exception {
        String token = tokenFor("plainuser", new SimpleGrantedAuthority("ROLE_USER"));
        UserDetails principal = new User("plainuser", "hashed", true, true, true, true,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        when(userAccountService.loadUserByUsername("plainuser")).thenReturn(principal);

        mockMvc.perform(get("/api/manual-journals").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void authenticatedPrincipalWithTheRequiredAuthoritySucceeds() throws Exception {
        String token = tokenFor("accountant", new SimpleGrantedAuthority("GL_VIEW"));
        UserDetails principal = new User("accountant", "hashed", true, true, true, true,
                List.of(new SimpleGrantedAuthority("GL_VIEW")));
        when(userAccountService.loadUserByUsername("accountant")).thenReturn(principal);
        when(manualJournalService.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/manual-journals").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void adminRoleAloneSatisfiesTheHasAuthorityOrHasRoleAdminFallback() throws Exception {
        // No GL_VIEW authority at all - ROLE_ADMIN alone must still satisfy
        // "hasAuthority('GL_VIEW') or hasRole('ADMIN')".
        String token = tokenFor("admin1", new SimpleGrantedAuthority("ROLE_ADMIN"));
        UserDetails principal = new User("admin1", "hashed", true, true, true, true,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        when(userAccountService.loadUserByUsername("admin1")).thenReturn(principal);
        when(manualJournalService.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/manual-journals").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void aCashierPrincipalResolvedThroughTheFallbackCanAlsoSatisfyRoleAdmin() throws Exception {
        // Proves the UserAccount -> Cashier fallback resolution actually participates in real
        // authorization decisions, not just in isolation (see JwtAuthenticationFilterTest).
        String token = tokenFor("adminCashier", new SimpleGrantedAuthority("ROLE_ADMIN"));
        UserDetails cashierPrincipal = new User("adminCashier", "hashed", true, true, true, true,
                List.of(new SimpleGrantedAuthority("ROLE_CASHIER"), new SimpleGrantedAuthority("ROLE_ADMIN")));
        when(userAccountService.loadUserByUsername("adminCashier")).thenThrow(new UsernameNotFoundException("not a user account"));
        when(cashierUserDetailsService.loadUserByUsername("adminCashier")).thenReturn(cashierPrincipal);
        when(manualJournalService.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/manual-journals").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}
