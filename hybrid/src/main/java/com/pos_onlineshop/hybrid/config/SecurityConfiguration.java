package com.pos_onlineshop.hybrid.config;



import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

import static org.springframework.security.config.http.SessionCreationPolicy.STATELESS;


@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfiguration {

    //private final LogoutHandler logoutHandler;
    //private final AuthenticationProvider authenticationProvider;

    private static final String[] WHITE_LIST_URL = {
            "/metrics",
            "/actuator/**",
"/**",
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
//                        .requestMatchers("/api/v1/deposit-percentage/**").hasAnyRole(ADMIN.name())
//                        .requestMatchers("/api/v1/customers/**").hasAnyRole(ADMIN.name())
//                        .requestMatchers("/api/v1/customer-packages/**").hasAnyRole(ADMIN.name())
//                        .requestMatchers("/api/v1/installation-fees/**").hasAnyRole(ADMIN.name())
//                        .requestMatchers("/api/v1/starlink-kits/**").hasAnyRole(ADMIN.name())
                        .anyRequest().authenticated()
                )
//                .addFilterBefore(null, UsernamePasswordAuthenticationFilter.class)
//                .authenticationProvider(authenticationProvider)
//                .logout(logout -> logout
//                        .logoutUrl("/api/v1/auth/logout")
//                      //  .addLogoutHandler(logoutHandler)
//                        .logoutSuccessHandler((request, response, authentication) -> SecurityContextHolder.clearContext())
   //             )

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
