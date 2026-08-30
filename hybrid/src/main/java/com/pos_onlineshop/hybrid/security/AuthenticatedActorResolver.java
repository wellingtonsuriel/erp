package com.pos_onlineshop.hybrid.security;

import com.pos_onlineshop.hybrid.services.UserAccountService;
import com.pos_onlineshop.hybrid.userAccount.UserAccount;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

/**
 * Resolves the real acting user from the JWT-authenticated principal rather than trusting a
 * client-supplied user id in a request body or query parameter. Maker-checker and audit-trail
 * integrity (e.g. "the preparer cannot also approve") depend on the acting user id being one the
 * caller cannot forge - a request body field alone can be set to any value by whoever holds a
 * valid token, regardless of which account it was issued to.
 */
@Component
@RequiredArgsConstructor
public class AuthenticatedActorResolver {

    private final UserAccountService userAccountService;

    /**
     * @throws IllegalStateException if there is no authenticated principal, or the principal's
     *         username does not resolve to a known account - both indicate the security filter
     *         chain let an unauthenticated or inconsistent request through to a controller that
     *         requires a real actor identity.
     */
    public Long requireActingUserId(UserDetails userDetails) {
        if (userDetails == null) {
            throw new IllegalStateException("No authenticated user for this action");
        }
        UserAccount account = userAccountService.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + userDetails.getUsername()));
        return account.getId();
    }
}
