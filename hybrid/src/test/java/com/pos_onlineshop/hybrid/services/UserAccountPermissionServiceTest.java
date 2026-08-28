package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.dtos.GrantAccountingPermissionRequest;
import com.pos_onlineshop.hybrid.dtos.UserAccountPermissionResponse;
import com.pos_onlineshop.hybrid.enums.AccountingPermission;
import com.pos_onlineshop.hybrid.userAccount.UserAccount;
import com.pos_onlineshop.hybrid.userAccount.UserAccountRepository;
import com.pos_onlineshop.hybrid.userAccountPermission.UserAccountPermission;
import com.pos_onlineshop.hybrid.userAccountPermission.UserAccountPermissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAccountPermissionServiceTest {

    @Mock private UserAccountPermissionRepository userAccountPermissionRepository;
    @Mock private UserAccountRepository userAccountRepository;

    private UserAccountPermissionService service;
    private UserAccount targetUser;
    private UserAccount adminUser;

    @BeforeEach
    void setUp() {
        service = new UserAccountPermissionService(userAccountPermissionRepository, userAccountRepository);
        targetUser = UserAccount.builder().id(1L).username("accountant1").build();
        adminUser = UserAccount.builder().id(2L).username("admin1").build();
    }

    private GrantAccountingPermissionRequest request(AccountingPermission permission, Long grantedBy) {
        GrantAccountingPermissionRequest request = new GrantAccountingPermissionRequest();
        request.setPermission(permission);
        request.setGrantedByUserId(grantedBy);
        return request;
    }

    @Test
    void grantSucceedsForANewPermission() {
        when(userAccountRepository.findById(1L)).thenReturn(Optional.of(targetUser));
        when(userAccountRepository.findById(2L)).thenReturn(Optional.of(adminUser));
        when(userAccountPermissionRepository.existsByUserAccountAndPermission(targetUser, AccountingPermission.GL_POST))
                .thenReturn(false);
        when(userAccountPermissionRepository.save(any(UserAccountPermission.class))).thenAnswer(inv -> inv.getArgument(0));

        UserAccountPermissionResponse response = service.grant(1L, request(AccountingPermission.GL_POST, 2L));

        assertEquals(AccountingPermission.GL_POST, response.getPermission());
        assertEquals("accountant1", response.getUsername());
        assertEquals("admin1", response.getGrantedByUsername());
    }

    @Test
    void grantRejectsADuplicatePermission() {
        when(userAccountRepository.findById(1L)).thenReturn(Optional.of(targetUser));
        when(userAccountPermissionRepository.existsByUserAccountAndPermission(targetUser, AccountingPermission.GL_POST))
                .thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> service.grant(1L, request(AccountingPermission.GL_POST, null)));
    }

    @Test
    void revokeRemovesAnExistingPermission() {
        UserAccountPermission existing = UserAccountPermission.builder()
                .id(10L).userAccount(targetUser).permission(AccountingPermission.AP_PAY).build();
        when(userAccountRepository.findById(1L)).thenReturn(Optional.of(targetUser));
        when(userAccountPermissionRepository.findByUserAccountAndPermission(targetUser, AccountingPermission.AP_PAY))
                .thenReturn(Optional.of(existing));

        assertDoesNotThrow(() -> service.revoke(1L, AccountingPermission.AP_PAY));
    }

    @Test
    void revokeThrowsWhenThePermissionWasNeverGranted() {
        when(userAccountRepository.findById(1L)).thenReturn(Optional.of(targetUser));
        when(userAccountPermissionRepository.findByUserAccountAndPermission(targetUser, AccountingPermission.AP_PAY))
                .thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.revoke(1L, AccountingPermission.AP_PAY));
    }

    @Test
    void grantThrowsWhenTargetUserDoesNotExist() {
        when(userAccountRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.grant(99L, request(AccountingPermission.GL_POST, null)));
    }
}
