package com.pos_onlineshop.hybrid.damagedStockReceived;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.pos_onlineshop.hybrid.cashier.Cashier;
import com.pos_onlineshop.hybrid.inventoryTransfer.InventoryTransfer;
import com.pos_onlineshop.hybrid.products.Product;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entity representing damaged stock received during an inventory transfer.
 * This tracks items that were damaged in transit and provides detailed
 * information about the damage for reporting and analysis purposes.
 */
@Entity
@Table(name = "damaged_stock_received")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DamagedStockReceived {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Reference to the inventory transfer where the damage occurred
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transfer_id", nullable = false)
    @NotNull(message = "Transfer is required")
    @JsonIgnore
    private InventoryTransfer transfer;

    /**
     * The product that was damaged
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "product_id", nullable = false)
    @NotNull(message = "Product is required")
    private Product product;

    /**
     * Quantity of damaged items
     */
    @Column(nullable = false)
    @NotNull(message = "Damaged quantity is required")
    @Min(value = 1, message = "Damaged quantity must be at least 1")
    private Integer damagedQuantity;

    /**
     * Unit cost of the damaged product at the time of transfer
     */
    @Column(nullable = false, precision = 19, scale = 2)
    @NotNull(message = "Unit cost is required")
    private BigDecimal unitCost;

    /**
     * Reason for the damage (e.g., "Broken during transit", "Water damage", "Packaging failure")
     */
    @Column(length = 500)
    private String damageReason;

    /**
     * Detailed description of the damage
     */
    @Column(length = 1000)
    private String damageDescription;

    /**
     * Severity of the damage (MINOR, MODERATE, SEVERE, TOTAL_LOSS)
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private DamageSeverity severity;

    /**
     * Whether the damaged items can be repaired or salvaged
     */
    @Column(nullable = false)
    private Boolean repairable = false;

    /**
     * Whether an insurance claim was filed for this damage
     */
    @Column(nullable = false)
    private Boolean insuranceClaimed = false;

    /**
     * Reference number for insurance claim (if applicable)
     */
    @Column(length = 100)
    private String insuranceClaimNumber;

    /**
     * Cashier who identified and reported the damage
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "reported_by")
    private Cashier reportedBy;

    /**
     * Date and time when the damage was identified
     */
    @Column(nullable = false)
    private LocalDateTime identifiedAt;

    /**
     * Additional notes about the damaged stock
     */
    @Column(length = 1000)
    private String notes;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Calculate the total value of damaged stock
     *
     * @return Total damage value (damagedQuantity * unitCost)
     */
    public BigDecimal getTotalDamageValue() {
        if (damagedQuantity == null || unitCost == null) {
            return BigDecimal.ZERO;
        }
        return unitCost.multiply(BigDecimal.valueOf(damagedQuantity));
    }

    /**
     * Set the identified at timestamp to current time if not already set
     */
    @PrePersist
    protected void onCreate() {
        if (identifiedAt == null) {
            identifiedAt = LocalDateTime.now();
        }
    }

    /**
     * Enum representing the severity of damage
     */
    public enum DamageSeverity {
        MINOR("Minor damage - cosmetic issues only"),
        MODERATE("Moderate damage - affects functionality"),
        SEVERE("Severe damage - product unusable"),
        TOTAL_LOSS("Total loss - complete destruction");

        private final String description;

        DamageSeverity(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}
