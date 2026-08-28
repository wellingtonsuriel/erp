package com.pos_onlineshop.hybrid.dtos;

import com.pos_onlineshop.hybrid.enums.AccountingPermission;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class UserAccountPermissionResponse {
    private Long id;
    private Long userAccountId;
    private String username;
    private AccountingPermission permission;
    private LocalDateTime grantedAt;
    private String grantedByUsername;
}
