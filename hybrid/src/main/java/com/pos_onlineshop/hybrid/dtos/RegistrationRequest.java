package com.pos_onlineshop.hybrid.dtos;

import lombok.Data;

@Data
public class RegistrationRequest {
    private String username;
    private String password;
    private String email;
}