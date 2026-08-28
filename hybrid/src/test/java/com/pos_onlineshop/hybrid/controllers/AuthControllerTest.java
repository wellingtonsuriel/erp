package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.dtos.AuthenticationRequest;
import com.pos_onlineshop.hybrid.dtos.AuthenticationResponse;
import com.pos_onlineshop.hybrid.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtService jwtService;
    @Mock private Authentication authentication;

    private AuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthController(authenticationManager, jwtService);
    }

    private AuthenticationRequest request(String username, String password) {
        AuthenticationRequest request = new AuthenticationRequest();
        request.setUsername(username);
        request.setPassword(password);
        return request;
    }

    @Test
    void loginReturnsATokenWithRolesAndPermissionsSeparated() {
        UserDetails userDetails = new User("accountant1", "hashed", true, true, true, true,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("GL_POST")));
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(jwtService.generateToken(userDetails)).thenReturn("signed.jwt.token");

        ResponseEntity<?> response = controller.login(request("accountant1", "password123"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        AuthenticationResponse body = (AuthenticationResponse) response.getBody();
        assertNotNull(body);
        assertEquals("signed.jwt.token", body.getToken());
        assertEquals("Bearer", body.getTokenType());
        assertEquals("accountant1", body.getUsername());
        assertTrue(body.getRoles().contains("ADMIN"));
        assertTrue(body.getPermissions().contains("GL_POST"));
        assertFalse(body.getPermissions().contains("ADMIN"));
    }

    @Test
    void loginReturnsUnauthorizedForBadCredentials() {
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("bad"));

        ResponseEntity<?> response = controller.login(request("accountant1", "wrong"));

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }
}
