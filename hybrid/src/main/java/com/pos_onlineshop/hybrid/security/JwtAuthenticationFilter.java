package com.pos_onlineshop.hybrid.security;

import com.pos_onlineshop.hybrid.services.UserAccountService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Populates the SecurityContext from a "Authorization: Bearer <jwt>" header when present and
 * valid. Deliberately does not reject a request for a missing/invalid token itself - it just
 * leaves the SecurityContext empty (anonymous), so URL/method authorization decides what
 * happens next. This lets method security (@PreAuthorize) work correctly without changing
 * SecurityConfiguration's existing permitAll URL matching for endpoints that don't request
 * authorization.
 *
 * A JWT's subject (see JwtService.generateToken) is a username in exactly one of two disjoint
 * namespaces: UserAccount (online customers, minted by AuthController's /api/auth/login) or
 * Cashier (POS/back-office staff, minted by CashierController's /api/cashiers/authenticate -
 * see CashierUserDetailsService's class comment for why these stay two entities rather than
 * being merged). Resolution tries UserAccount first, then falls back to Cashier, since the two
 * username columns are independently unique but not guaranteed disjoint from each other.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserAccountService userAccountService;
    private final CashierUserDetailsService cashierUserDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader(HEADER);
        if (header == null || !header.startsWith(PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(PREFIX.length());
        try {
            String username = jwtService.extractUsername(token);
            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = resolveUserDetails(username);
                if (jwtService.isTokenValid(token, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (UsernameNotFoundException e) {
            log.debug("JWT referenced an unknown user: {}", e.getMessage());
        } catch (Exception e) {
            log.debug("Rejected invalid JWT: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private UserDetails resolveUserDetails(String username) {
        try {
            return userAccountService.loadUserByUsername(username);
        } catch (UsernameNotFoundException e) {
            return cashierUserDetailsService.loadUserByUsername(username);
        }
    }
}
