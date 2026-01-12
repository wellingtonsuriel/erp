package com.pos_onlineshop.hybrid.fiscalDevice;

import com.pos_onlineshop.hybrid.enums.FiscalDeviceType;
import com.pos_onlineshop.hybrid.shop.Shop;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Represents a ZIMRA certified fiscal device
 */
@Entity
@Table(name = "fiscal_devices")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FiscalDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String serialNumber;

    @Column(nullable = false)
    private String deviceName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FiscalDeviceType deviceType;

    @ManyToOne
    @JoinColumn(name = "shop_id")
    private Shop shop;

    @Column(nullable = false)
    private String zimraRegistrationNumber;

    private String fiscalMemoryId;

    @Column(nullable = false)
    private LocalDateTime certificationDate;

    private LocalDateTime certificationExpiryDate;

    private String manufacturer;

    private String model;

    private String firmwareVersion;

    private String apiEndpoint;  // For cloud/API based devices

    private String apiKey;       // For authentication

    @Column(nullable = false)
    private Boolean isActive = true;

    @Column(nullable = false)
    private Boolean isConnected = false;

    private LocalDateTime lastConnectionTime;

    private String lastErrorMessage;

    private Long lastReceiptNumber;  // Last issued receipt number

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (lastReceiptNumber == null) {
            lastReceiptNumber = 0L;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Get the next receipt number for this device
     */
    public synchronized Long getNextReceiptNumber() {
        lastReceiptNumber++;
        return lastReceiptNumber;
    }

    /**
     * Check if device is operational
     */
    public boolean isOperational() {
        return isActive && isConnected;
    }

    /**
     * Check if certification is valid
     */
    public boolean isCertificationValid() {
        if (certificationExpiryDate == null) {
            return true; // No expiry set
        }
        return LocalDateTime.now().isBefore(certificationExpiryDate);
    }
}
