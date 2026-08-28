package com.pos_onlineshop.hybrid.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private final JwtService jwtService = new JwtService(
            "test-only-secret-key-must-be-at-least-256-bits-long-for-hs256!!", 60_000L);

    private UserDetails user(String username) {
        return new User(username, "irrelevant", true, true, true, true, List.of());
    }

    @Test
    void generatesATokenThatExtractsTheOriginalUsername() {
        String token = jwtService.generateToken(user("alice"));
        assertEquals("alice", jwtService.extractUsername(token));
    }

    @Test
    void tokenIsValidForTheUserItWasIssuedTo() {
        UserDetails alice = user("alice");
        String token = jwtService.generateToken(alice);
        assertTrue(jwtService.isTokenValid(token, alice));
    }

    @Test
    void tokenIsNotValidForADifferentUser() {
        String token = jwtService.generateToken(user("alice"));
        assertFalse(jwtService.isTokenValid(token, user("bob")));
    }

    @Test
    void tokenSignedWithADifferentSecretIsRejected() {
        JwtService otherService = new JwtService(
                "a-completely-different-secret-key-also-256-bits-long-enough!!!", 60_000L);
        String token = otherService.generateToken(user("alice"));

        assertThrows(Exception.class, () -> jwtService.extractUsername(token));
    }

    @Test
    void expiredTokenIsNotValid() throws InterruptedException {
        JwtService shortLivedService = new JwtService(
                "test-only-secret-key-must-be-at-least-256-bits-long-for-hs256!!", 1L);
        UserDetails alice = user("alice");
        String token = shortLivedService.generateToken(alice);
        Thread.sleep(10);

        assertThrows(Exception.class, () -> shortLivedService.isTokenValid(token, alice));
    }
}
