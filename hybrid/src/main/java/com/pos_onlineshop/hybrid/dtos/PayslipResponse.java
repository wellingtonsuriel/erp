package com.pos_onlineshop.hybrid.dtos;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class PayslipResponse {
    private Long id;
    private Long employeeId;
    private String employeeNumber;
    private String employeeName;
    private String currencyCode;
    private BigDecimal grossPay;
    private BigDecimal totalDeductions;
    private BigDecimal netPay;
}
