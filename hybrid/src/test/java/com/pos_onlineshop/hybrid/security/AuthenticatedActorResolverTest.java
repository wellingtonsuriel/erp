package com.pos_onlineshop.hybrid.security;

import com.pos_onlineshop.hybrid.services.UserAccountService;
import com.pos_onlineshop.hybrid.userAccount.UserAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * The acting-user id for maker-checker actions (manual journal approve/reject/post, expense
 * approve/reject, accrual reverse, ...) must always come from the JWT-authenticated principal,
 * never a client-supplied request field - otherwise anyone holding a valid token could set
 * "userId" to a different account's id and forge an approval, silently defeating the
 * "preparer cannot approve their own journal" check that every one of those domain models
 * enforces by comparing the *stored* preparer id against the *resolved* approver id.
 */
@ExtendWith(MockitoExtension.class)
class AuthenticatedActorResolverTest {

    @Mock private UserAccountService userAccountService;

    private AuthenticatedActorResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new AuthenticatedActorResolver(userAccountService);
    }

    @Test
    void resolvesTheRealAccountIdForTheAuthenticatedUsername() {
        UserDetails principal = new User("approver1", "hashed", List.of());
        UserAccount account = new UserAccount();
        account.setId(42L);
        when(userAccountService.findByUsername("approver1")).thenReturn(Optional.of(account));

        Long resolved = resolver.requireActingUserId(principal);

        assertEquals(42L, resolved);
    }

    @Test
    void rejectsAMissingPrincipalRatherThanFallingBackToAnyDefault() {
        assertThrows(IllegalStateException.class, () -> resolver.requireActingUserId(null));
    }

    @Test
    void rejectsAPrincipalWhoseUsernameNoLongerResolvesToAnAccount() {
        UserDetails principal = new User("ghost", "hashed", List.of());
        when(userAccountService.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> resolver.requireActingUserId(principal));
    }
}
