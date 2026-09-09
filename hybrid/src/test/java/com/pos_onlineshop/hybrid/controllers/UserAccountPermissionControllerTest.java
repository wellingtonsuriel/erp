package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.dtos.GrantAccountingPermissionRequest;
import com.pos_onlineshop.hybrid.dtos.UserAccountPermissionResponse;
import com.pos_onlineshop.hybrid.enums.AccountingPermission;
import com.pos_onlineshop.hybrid.services.UserAccountPermissionService;
import com.pos_onlineshop.hybrid.services.UserAccountService;
import com.pos_onlineshop.hybrid.userAccount.UserAccount;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * grant previously trusted GrantAccountingPermissionRequest.grantedByUserId from the request
 * body - any admin/USER_ADMIN caller could claim any user id as the grantor, including
 * impersonating a different admin. This test confirms the controller now always resolves the
 * real principal.
 */
@ExtendWith(MockitoExtension.class)
class UserAccountPermissionControllerTest {

    @Mock private UserAccountPermissionService userAccountPermissionService;
    @Mock private UserAccountService userAccountService;

    private UserAccountPermissionController controller;

    @Test
    void grantUsesTheAuthenticatedPrincipalNeverTheRequestBody() {
        controller = new UserAccountPermissionController(userAccountPermissionService, userAccountService);
        UserAccount realGrantor = UserAccount.builder().id(11L).username("real-admin").build();
        when(userAccountService.findByUsername("real-admin")).thenReturn(Optional.of(realGrantor));
        when(userAccountPermissionService.grant(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(11L)))
                .thenReturn(UserAccountPermissionResponse.builder().id(1L).build());

        GrantAccountingPermissionRequest request = new GrantAccountingPermissionRequest();
        request.setPermission(AccountingPermission.GL_POST);
        request.setGrantedByUserId(9999L); // attacker-controlled value that must be ignored

        UserDetails principal = new User("real-admin", "hashed", true, true, true, true, List.of());
        ResponseEntity<?> response = controller.grant(1L, request, principal);

        verify(userAccountPermissionService).grant(1L, request, 11L);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }
}
