package com.pos_onlineshop.hybrid.config;



import com.pos_onlineshop.hybrid.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

import static org.springframework.security.config.http.SessionCreationPolicy.STATELESS;

/**
 * NOTE on WHITE_LIST_URL: this used to include the literal pattern "/**", which matched every
 * URL and made .anyRequest().authenticated() below unreachable dead code - the entire HTTP
 * layer permitted anonymous access regardless of any @PreAuthorize annotation (audit finding
 * P0-1/"global permit-all"). That was only safe in the narrow sense that login never issued a
 * real token anyway (see CashierUserDetailsService/AuthController's class comments) - now that
 * it does, every endpoint requires authentication by default, and only the small set of
 * endpoints below - login/registration entry points, the WebSocket handshake, health checks,
 * and API documentation - are genuinely meant to be reachable without one. Every controller in
 * this codebase was re-audited before this change landed to make sure each one carries the
 * @PreAuthorize it actually needs (see e.g. POSController, InventoryTransferController,
 * SellingPriceController, ShopController, ZimraController's class comments for what changed).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfiguration {

    private final AuthenticationProvider authenticationProvider;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    private static final String[] WHITE_LIST_URL = {
            // Login/registration - must be reachable without a token to obtain one.
            "/api/auth/login",
            "/api/cashiers/authenticate",
            "/api/cashiers/authenticate/pin",
            "/api/users/register",
            // WebSocket handshake (SockJS). The client-publishable /app/inventory-update and
            // /app/order-notification destinations this whitelist entry used to make reachable
            // without message-level authorization were removed entirely (WebSocketController
            // deleted) rather than secured - they had no real caller and just echoed whatever a
            // connected client sent straight to every subscriber of /topic/inventory or
            // /topic/orders, which is indistinguishable from a genuine server-verified update. The
            // real ones are pushed directly by InventoryService/OrderService via
            // SimpMessagingTemplate, never client-initiated, so nothing legitimate used this relay.
            "/ws/**",
            // Ops/monitoring - only /actuator/health is exposed by Spring Boot's own default
            // (no management.endpoints.web.exposure.include is configured), so this is
            // intentionally narrower than a blanket "/actuator/**".
            "/actuator/health",
            "/actuator/health/**",
            // API documentation.
            "/v2/api-docs",
            "/v3/api-docs",
            "/v3/api-docs/**",
            "/swagger-resources",
            "/swagger-resources/**",
            "/configuration/ui",
            "/configuration/security",
            "/swagger-ui/**",
            "/webjars/**",
            "/swagger-ui.html"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource())) // Attach CORS configuration
                .sessionManagement(session -> session.sessionCreationPolicy(STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(WHITE_LIST_URL).permitAll()
                        .anyRequest().authenticated()
                )
                // Without an explicit entry point, Spring Security falls back to
                // Http403ForbiddenEntryPoint for a request with no authentication at all,
                // making it indistinguishable from an authenticated-but-forbidden 403 from
                // @PreAuthorize - this makes an unauthenticated/missing/invalid-token request
                // correctly return 401 instead, so clients (and this class's own
                // SecurityConfigurationIntegrationTest) can tell "you're not logged in" apart
                // from "you're logged in but not allowed to do this".
                .exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .authenticationProvider(authenticationProvider)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
        ;

        return http.build();
    }

    @Bean
    public UrlBasedCorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("*")); // Allow your frontend origin /// Arrays.asList("*") list all ips for front end
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS")); // Allowed HTTP methods
        //config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE"));
        config.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type")); // Allowed headers
        config.setAllowCredentials(false); // Allow cookies or credentials if necessary // put true when you list set to true

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config); // Apply CORS settings globally
        return source;
    }
}
