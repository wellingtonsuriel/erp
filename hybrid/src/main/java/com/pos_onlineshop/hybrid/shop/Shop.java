package com.pos_onlineshop.hybrid.shop;

import com.pos_onlineshop.hybrid.cashier.Cashier;
import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.enums.CashierRole;
import com.pos_onlineshop.hybrid.enums.ShopType;
import com.pos_onlineshop.hybrid.shopInventory.ShopInventory;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Shop entity representing physical store locations or warehouse.
 * Now includes multi-currency support with default currency per shop.
 */
@Entity
@Table(name = "shops")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"assignedCashiers", "shopInventories", "managedByCashiers"})
@ToString(exclude = {"assignedCashiers", "shopInventories", "managedByCashiers"})
public class Shop {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code; // e.g., "SHOP-001", "WAREHOUSE"

    @Column(nullable = false)
    private String name;

    @Column(length = 1000)
    private String address;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "email")
    private String email;

    @ManyToOne
    @JoinColumn(name = "default_currency_id", nullable = false)
    private Currency defaultCurrency; // Shop's default currency

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ShopType type = ShopType.RETAIL;

    @Column(name = "is_active")
    @Builder.Default
    private boolean active = true;

    @Column(name = "opening_time")
    private String openingTime; // e.g., "09:00"

    @Column(name = "closing_time")
    private String closingTime; // e.g., "21:00"

    @ManyToOne
    @JoinColumn(name = "manager_id")
    private Cashier manager; // Shop manager (Cashier with MANAGER role)

    @OneToMany(mappedBy = "assignedShop", fetch = FetchType.LAZY)
    @Builder.Default
    private Set<Cashier> assignedCashiers = new HashSet<>();

    @OneToMany(mappedBy = "shop", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private Set<ShopInventory> shopInventories = new HashSet<>();

    // Cashiers who can manage this shop (besides the primary manager)
    @ManyToMany
    @JoinTable(
            name = "shop_managers",
            joinColumns = @JoinColumn(name = "shop_id"),
            inverseJoinColumns = @JoinColumn(name = "cashier_id")
    )
    @Builder.Default
    private Set<Cashier> managedByCashiers = new HashSet<>();

    @Column(name = "max_cashiers")
    private Integer maxCashiers; // Maximum number of cashiers allowed

    @Column(name = "storage_capacity")
    private Integer storageCapacity; // General storage capacity indicator

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

    public boolean isWarehouse() {
        return this.type == ShopType.WAREHOUSE;
    }

    public boolean canManageShop(Cashier cashier) {
        return (manager != null && manager.equals(cashier)) ||
                managedByCashiers.contains(cashier) ||
                cashier.getRole() == CashierRole.ADMIN;
    }

    public boolean hasCapacityForCashier() {
        return maxCashiers == null || assignedCashiers.size() < maxCashiers;
    }
}