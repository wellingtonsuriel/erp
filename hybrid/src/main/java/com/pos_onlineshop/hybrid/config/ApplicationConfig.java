package com.pos_onlineshop.hybrid.config;

import com.pos_onlineshop.hybrid.services.UserAccountService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.authentication.logout.SimpleUrlLogoutSuccessHandler;

/** UserAccountService is taken as a @Bean method parameter (not a class-level constructor
 * field) deliberately: UserAccountService itself depends on the passwordEncoder() bean
 * defined below, so a constructor-level dependency here would make this class require a
 * not-yet-buildable UserAccountService just to produce passwordEncoder() - a real circular
 * dependency. As a method parameter it is only resolved when authenticationProvider() itself
 * is built, by which point passwordEncoder() is already available. */
@Configuration
public class ApplicationConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** Wires UserAccountService (already a UserDetailsService - see its class) together with
     * the BCrypt encoder so username/password login actually works. This bean did not exist
     * before, despite AuthenticationManager/AuthenticationConfiguration already being wired -
     * without it, authenticate() had nothing to delegate to. */
    @Bean
    public AuthenticationProvider authenticationProvider(UserAccountService userAccountService) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userAccountService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public LogoutSuccessHandler logoutSuccessHandler() {
        return new SimpleUrlLogoutSuccessHandler(); // You can customize this handler as needed
    }

}
