package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.dtos.GrantAccountingPermissionRequest;
import com.pos_onlineshop.hybrid.dtos.UserAccountPermissionResponse;
import com.pos_onlineshop.hybrid.enums.AccountingPermission;
import com.pos_onlineshop.hybrid.userAccount.UserAccount;
import com.pos_onlineshop.hybrid.userAccount.UserAccountRepository;
import com.pos_onlineshop.hybrid.userAccountPermission.UserAccountPermission;
import com.pos_onlineshop.hybrid.userAccountPermission.UserAccountPermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/** Administration for AccountingPermission grants - see AccountingPermission's Javadoc for
 * how these differ from the ROLE_* model and from the POS-terminal CashierPermission model. */
@Service
@RequiredArgsConstructor
@Transactional
public class UserAccountPermissionService {

    private final UserAccountPermissionRepository userAccountPermissionRepository;
    private final UserAccountRepository userAccountRepository;

    public List<UserAccountPermissionResponse> findForUser(Long userAccountId) {
        UserAccount user = findUserOrThrow(userAccountId);
        return userAccountPermissionRepository.findByUserAccount(user).stream()
                .map(this::toResponse).collect(Collectors.toList());
    }

    public UserAccountPermissionResponse grant(Long userAccountId, GrantAccountingPermissionRequest request) {
        UserAccount user = findUserOrThrow(userAccountId);
        if (userAccountPermissionRepository.existsByUserAccountAndPermission(user, request.getPermission())) {
            throw new IllegalArgumentException(
                    "User " + user.getUsername() + " already has permission " + request.getPermission());
        }
        UserAccount grantedBy = request.getGrantedByUserId() != null
                ? findUserOrThrow(request.getGrantedByUserId())
                : null;

        UserAccountPermission permission = UserAccountPermission.builder()
                .userAccount(user)
                .permission(request.getPermission())
                .grantedBy(grantedBy)
                .build();
        return toResponse(userAccountPermissionRepository.save(permission));
    }

    public void revoke(Long userAccountId, AccountingPermission permission) {
        UserAccount user = findUserOrThrow(userAccountId);
        UserAccountPermission existing = userAccountPermissionRepository.findByUserAccountAndPermission(user, permission)
                .orElseThrow(() -> new IllegalArgumentException(
                        "User " + user.getUsername() + " does not have permission " + permission));
        userAccountPermissionRepository.delete(existing);
    }

    private UserAccount findUserOrThrow(Long userAccountId) {
        return userAccountRepository.findById(userAccountId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userAccountId));
    }

    private UserAccountPermissionResponse toResponse(UserAccountPermission permission) {
        return UserAccountPermissionResponse.builder()
                .id(permission.getId())
                .userAccountId(permission.getUserAccount().getId())
                .username(permission.getUserAccount().getUsername())
                .permission(permission.getPermission())
                .grantedAt(permission.getGrantedAt())
                .grantedByUsername(permission.getGrantedBy() != null ? permission.getGrantedBy().getUsername() : null)
                .build();
    }
}
