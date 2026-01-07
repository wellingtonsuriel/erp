package com.pos_onlineshop.hybrid.dtos;

import lombok.Data;

@Data
public class PinLoginRequest {
    private String employeeId;
    private String pin;
}