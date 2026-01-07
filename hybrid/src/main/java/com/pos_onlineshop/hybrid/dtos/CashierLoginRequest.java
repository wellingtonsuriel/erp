package com.pos_onlineshop.hybrid.dtos;

import lombok.Data;

@Data
public class CashierLoginRequest {
    private String username;
    private String password;
}
