package com.pos_onlineshop.hybrid.dtos;

import com.pos_onlineshop.hybrid.userAccount.UserAccount;
import lombok.Data;

@Data
public class UpdateUserRequest {
    private String email;
    private String password;

    public UserAccount toUserAccount() {
        return UserAccount.builder()
                .email(email)
                .password(password)
                .build();
    }
}