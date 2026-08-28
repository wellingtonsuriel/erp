package com.pos_onlineshop.hybrid.dtos;

import com.pos_onlineshop.hybrid.enums.AccountingPermission;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class GrantAccountingPermissionRequest {

    @NotNull(message = "Permission is required")
    private AccountingPermission permission;

    /** UserAccount id of the admin granting this permission, for the audit trail. */
    private Long grantedByUserId;
}
