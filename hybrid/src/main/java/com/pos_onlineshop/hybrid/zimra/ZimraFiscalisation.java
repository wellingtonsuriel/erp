package com.pos_onlineshop.hybrid.zimra;

import com.pos_onlineshop.hybrid.cashier.Cashier;
import com.pos_onlineshop.hybrid.customers.Customers;
import com.pos_onlineshop.hybrid.enums.FiscalDocumentType;
import com.pos_onlineshop.hybrid.enums.FiscalStatus;
import com.pos_onlineshop.hybrid.fiscalDevice.FiscalDevice;
import com.pos_onlineshop.hybrid.orders.Order;
import com.pos_onlineshop.hybrid.sales.Sales;
import com.pos_onlineshop.hybrid.shop.Shop;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Stores ZIMRA fiscalisation data for sales and orders
 * This is the permanent fiscal record required for tax compliance
 */
@Entity
@Table(name = "zimra_fiscalisations", indexes = {
    @Index(name = "idx_fiscal_code", columnList = "fiscalCode", unique = true),
    @Index(name = "idx_receipt_number", columnList = "receiptNumber"),
    @Index(name = "idx_verification_code", columnList = "verificationCode"),
    @Index(name = "idx_fiscal_date", columnList = "fiscalDate"),
    @Index(name = "idx_shop", columnList = "shop_id"),
    @Index(name = "idx_status", columnList = "status")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ZimraFiscalisation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Reference to the original transaction
    @ManyToOne
    @JoinColumn(name = "order_id")
    private Order order;

    @ManyToOne
    @JoinColumn(name = "sale_id")
    private Sales sale;

    @ManyToOne
    @JoinColumn(name = "shop_id", nullable = false)
    private Shop shop;

    @ManyToOne
    @JoinColumn(name = "fiscal_device_id", nullable = false)
    private FiscalDevice fiscalDevice;

    @ManyToOne
    @JoinColumn(name = "cashier_id")
    private Cashier cashier;

    @ManyToOne
    @JoinColumn(name = "customer_id")
    private Customers customer;

    // Fiscal Document Information
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FiscalDocumentType documentType;

    @Column(nullable = false, unique = true, length = 100)
    private String fiscalCode;  // Unique fiscal identifier (QR code content)

    @Column(nullable = false, length = 50)
    private String receiptNumber;  // Sequential receipt number from fiscal device

    @Column(nullable = false, length = 100)
    private String verificationCode;  // ZIMRA verification code

    @Column(nullable = false, columnDefinition = "TEXT")
    private String digitalSignature;  // Cryptographic signature

    // Financial Information
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal subtotalAmount;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal taxAmount;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal taxRate;  // Tax rate percentage (e.g., 15.00 for 15% VAT)

    @Column(nullable = false, length = 3)
    private String currency;

    private BigDecimal exchangeRate;  // If not base currency

    // Tax Details
    private String taxpayerTin;  // Tax Identification Number of buyer (if registered)

    private String taxpayerName;  // Name of registered taxpayer

    @Column(nullable = false)
    private String businessTin;  // TIN of the business (seller)

    @Column(nullable = false)
    private String businessName;  // Name of the business

    private String businessAddress;

    // Status and Timing
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FiscalStatus status;

    @Column(nullable = false)
    private LocalDateTime fiscalDate;  // Date/time of fiscalisation

    private LocalDateTime verificationDate;  // Date/time verified with ZIMRA

    private LocalDateTime cancellationDate;

    private String cancellationReason;

    // Technical Details
    private String fiscalMemoryNumber;  // Fiscal memory ID from device

    private Long fiscalCounterValue;  // Counter value at time of fiscalisation

    @Column(columnDefinition = "TEXT")
    private String qrCodeData;  // QR code content for customer verification

    @Column(columnDefinition = "TEXT")
    private String rawDeviceResponse;  // Raw response from fiscal device

    @Column(columnDefinition = "TEXT")
    private String errorMessage;  // Error details if fiscalisation failed

    private Integer retryCount = 0;  // Number of fiscalisation attempts

    // Audit Trail
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @Column(columnDefinition = "TEXT")
    private String auditLog;  // JSON log of all state changes

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (fiscalDate == null) {
            fiscalDate = LocalDateTime.now();
        }
        if (status == null) {
            status = FiscalStatus.PENDING;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Check if fiscalisation was successful
     */
    public boolean isFiscalised() {
        return status == FiscalStatus.FISCALISED || status == FiscalStatus.VERIFIED;
    }

    /**
     * Check if fiscalisation can be retried
     */
    public boolean canRetry() {
        return status == FiscalStatus.FAILED && retryCount < 3;
    }

    /**
     * Get full fiscal identifier for display
     */
    public String getFullFiscalIdentifier() {
        return String.format("%s-%s", receiptNumber, verificationCode);
    }

    /**
     * Generate QR code URL for customer verification
     */
    public String getVerificationUrl() {
        return String.format("https://verify.zimra.co.zw/%s", verificationCode);
    }

    /**
     * Check if this is an order or sale transaction
     */
    public boolean isOrder() {
        return order != null;
    }

    public boolean isSale() {
        return sale != null;
    }
}
