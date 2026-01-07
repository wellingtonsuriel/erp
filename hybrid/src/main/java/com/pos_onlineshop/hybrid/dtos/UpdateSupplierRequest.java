package com.pos_onlineshop.hybrid.dtos;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class UpdateSupplierRequest {
    private String code;
    private String name;
    private String contactPerson;
    private String email;
    private String phoneNumber;
    private String mobileNumber;
    private String faxNumber;
    private String address;
    private String city;
    private String state;
    private String country;
    private String postalCode;
    private String taxId;
    private String registrationNumber;
    private String paymentTerms;
    private BigDecimal creditLimit;
    private String website;
    private String notes;
    private Integer rating;
    private Integer leadTimeDays;
    private BigDecimal minimumOrderAmount;
}
