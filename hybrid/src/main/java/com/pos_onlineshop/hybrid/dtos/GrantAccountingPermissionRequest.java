package com.pos_onlineshop.hybrid.dtos;

import com.pos_onlineshop.hybrid.enums.AccountingPermission;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class GrantAccountingPermissionRequest {

    @NotNull(message = "Permission is required")
    private AccountingPermission permission;

    // Ignored server-side: the grantor is always the authenticated caller (see
    // UserAccountPermissionController/UserAccountPermissionService's class comments), never
    // this request-body field.
    private Long grantedByUserId;
}
