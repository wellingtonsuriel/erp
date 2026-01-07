package com.pos_onlineshop.hybrid.dtos;

import com.pos_onlineshop.hybrid.enums.Role;
import lombok.Data;

@Data
public class AddRoleRequest {
    private Role role;
}