package com.pos_onlineshop.hybrid.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.pos_onlineshop.hybrid.cashier.Cashier;
import com.pos_onlineshop.hybrid.enums.CashierRole;
import com.pos_onlineshop.hybrid.enums.Role;
import com.pos_onlineshop.hybrid.userAccount.UserAccount;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Both UserAccount and Cashier were being serialized back to API clients (register, getAll,
 * getById, and the authenticate responses) with their bcrypt password hash - and, for Cashier,
 * its bcrypt-hashed PIN - included in the JSON body. A leaked bcrypt hash of a short numeric PIN
 * is trivially brute-forceable offline, so these must never leave the server.
 */
class CredentialSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void userAccountJsonNeverIncludesThePasswordHash() throws Exception {
        UserAccount user = UserAccount.builder()
                .id(1L).username("alice").password("$2a$10$hashedpassword").email("alice@example.com")
                .enabled(true).roles(java.util.Set.of(Role.USER)).build();

        String json = mapper.writeValueAsString(user);

        assertFalse(json.contains("password"), "UserAccount JSON must not expose the password field: " + json);
        assertFalse(json.contains("hashedpassword"));
        assertTrue(json.contains("alice"));
    }

    @Test
    void cashierJsonNeverIncludesThePasswordOrPinHash() throws Exception {
        Cashier cashier = Cashier.builder()
                .id(1L).employeeId("EMP1").username("cashier1").password("$2a$10$hashedpassword")
                .firstName("A").lastName("B").email("cashier1@shop.com").role(CashierRole.CASHIER)
                .active(true).pinCode("$2a$10$hashedpin").build();

        String json = mapper.writeValueAsString(cashier);

        assertFalse(json.contains("\"password\""), "Cashier JSON must not expose the password field: " + json);
        assertFalse(json.contains("\"pinCode\""), "Cashier JSON must not expose the pinCode field: " + json);
        assertFalse(json.contains("hashedpassword"));
        assertFalse(json.contains("hashedpin"));
        assertTrue(json.contains("cashier1"));
    }
}
