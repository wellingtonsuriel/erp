package com.pos_onlineshop.hybrid.dtos;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AuthenticationResponse {
    private String token;
    private String tokenType;
    private String username;
    private List<String> roles;
    private List<String> permissions;
}
