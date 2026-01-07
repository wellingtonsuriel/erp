package com.pos_onlineshop.hybrid.dtos;

import com.pos_onlineshop.hybrid.enums.Permission;
import lombok.Data;

@Data
public class GrantPermissionRequest {
    private Permission permission;
    private Long grantedById;
}