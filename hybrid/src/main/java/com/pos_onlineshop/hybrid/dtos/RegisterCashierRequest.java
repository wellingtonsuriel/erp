package com.pos_onlineshop.hybrid.dtos;

import com.pos_onlineshop.hybrid.enums.CashierRole;
import lombok.Data;

@Data
public class RegisterCashierRequest {
    private String employeeId;
    private String username;
    private String password;
    private String firstName;
    private String lastName;
    private String email;
    private String phoneNumber;
    private CashierRole role = CashierRole.CASHIER;
    private String pinCode;
}