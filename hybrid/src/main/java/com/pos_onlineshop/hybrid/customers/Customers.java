package com.pos_onlineshop.hybrid.customers;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Customers entity representing customers in the system.
 * Tracks customer information, contact details, and business terms.
 */
@Entity
@Table(name = "customers")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Customers {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code; // Unique customer code (e.g., "CUST-001")

    @Column(nullable = false)
    private String name; // Customer name

    @Column(name = "email")
    private String email;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "mobile_number")
    private String mobileNumber;

    @Column(length = 1000)
    private String address;

    @Column(name = "city")
    private String city;

    @Column(name = "state")
    private String state;

    @Column(name = "country")
    private String country;

    @Column(name = "postal_code")
    private String postalCode;

    @Column(name = "tax_id")
    private String taxId; // Tax ID or VAT number

    @Column(name = "payment_terms")
    private String paymentTerms; // e.g., "Net 30", "Net 60", "COD"

    @Column(name = "credit_limit", precision = 19, scale = 4)
    private BigDecimal creditLimit;

    @Column(length = 2000)
    private String notes; // Additional notes about the customer

    @Column(name = "is_active")
    @Builder.Default
    private boolean active = true;

    @Column(name = "is_verified")
    @Builder.Default
    private boolean verified = false; // Whether customer is verified/approved

    @Column(name = "loyalty_points")
    @Builder.Default
    private Integer loyaltyPoints = 0; // Customer loyalty points

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Get full address formatted as a single string
     */
    public String getFullAddress() {
        StringBuilder fullAddress = new StringBuilder();
        if (address != null && !address.isEmpty()) {
            fullAddress.append(address);
        }
        if (city != null && !city.isEmpty()) {
            if (fullAddress.length() > 0) fullAddress.append(", ");
            fullAddress.append(city);
        }
        if (state != null && !state.isEmpty()) {
            if (fullAddress.length() > 0) fullAddress.append(", ");
            fullAddress.append(state);
        }
        if (postalCode != null && !postalCode.isEmpty()) {
            if (fullAddress.length() > 0) fullAddress.append(" ");
            fullAddress.append(postalCode);
        }
        if (country != null && !country.isEmpty()) {
            if (fullAddress.length() > 0) fullAddress.append(", ");
            fullAddress.append(country);
        }
        return fullAddress.toString();
    }

    /**
     * Get display name with code
     */
    public String getDisplayName() {
        return code != null ? String.format("%s (%s)", name, code) : name;
    }

    /**
     * Check if customer is active
     */
    public boolean isActiveCustomer() {
        return active && verified;
    }

    /**
     * Check if customer has contact information
     */
    public boolean hasContactInfo() {
        return (email != null && !email.isEmpty()) ||
                (phoneNumber != null && !phoneNumber.isEmpty()) ||
                (mobileNumber != null && !mobileNumber.isEmpty());
    }
}
