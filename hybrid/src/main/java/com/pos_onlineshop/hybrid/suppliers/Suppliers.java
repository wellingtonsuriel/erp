package com.pos_onlineshop.hybrid.suppliers;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Suppliers entity representing product suppliers in the system.
 * Tracks supplier information, contact details, and business terms.
 */
@Entity
@Table(name = "suppliers")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Suppliers {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code; // Unique supplier code (e.g., "SUP-001")

    @Column(nullable = false)
    private String name; // Supplier company name

    @Column(name = "contact_person")
    private String contactPerson; // Contact person name

    @Column(name = "email")
    private String email;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "mobile_number")
    private String mobileNumber;

    @Column(name = "fax_number")
    private String faxNumber;

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

    @Column(name = "tax_id", unique = true)
    private String taxId; // Tax ID or VAT number

    @Column(name = "registration_number")
    private String registrationNumber; // Business registration number

    @Column(name = "payment_terms")
    private String paymentTerms; // e.g., "Net 30", "Net 60", "COD"

    @Column(name = "credit_limit", precision = 19, scale = 4)
    private java.math.BigDecimal creditLimit;

    @Column(name = "website")
    private String website;

    @Column(length = 2000)
    private String notes; // Additional notes about the supplier

    @Column(name = "is_active")
    @Builder.Default
    private boolean active = true;

    @Column(name = "is_verified")
    @Builder.Default
    private boolean verified = false; // Whether supplier is verified/approved

    @Column(name = "rating")
    private Integer rating; // Supplier rating (1-5)

    @Column(name = "lead_time_days")
    private Integer leadTimeDays; // Average delivery lead time in days

    @Column(name = "minimum_order_amount", precision = 19, scale = 4)
    private java.math.BigDecimal minimumOrderAmount;

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
     * Check if supplier is available for orders
     */
    public boolean isAvailableForOrders() {
        return active && verified;
    }

    /**
     * Check if supplier has contact information
     */
    public boolean hasContactInfo() {
        return (email != null && !email.isEmpty()) ||
                (phoneNumber != null && !phoneNumber.isEmpty()) ||
                (mobileNumber != null && !mobileNumber.isEmpty());
    }
}
