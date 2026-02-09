package com.pos_onlineshop.hybrid.cashier;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.pos_onlineshop.hybrid.cashierSessions.CashierSession;
import com.pos_onlineshop.hybrid.enums.CashierRole;
import com.pos_onlineshop.hybrid.orders.Order;
import com.pos_onlineshop.hybrid.shop.Shop;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Cashier entity for POS staff members.
 * Separate from UserAccount which is for online customers.
 */
@Entity
@Table(name = "cashiers")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"assignedShop", "cashierSessions", "processedOrders"})
@ToString(exclude = {"password", "cashierSessions", "processedOrders"})
public class Cashier {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String employeeId; // e.g., "EMP001"

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String password; // Encrypted

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "phone_number")
    private String phoneNumber;

    @ManyToOne
    @JoinColumn(name = "assigned_shop_id")
    @JsonIgnore
    private Shop assignedShop;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private CashierRole role = CashierRole.CASHIER;

    @Column(name = "is_active")
    @Builder.Default
    private boolean active = true;

    @Column(name = "hire_date")
    private LocalDateTime hireDate;

    @Column(name = "last_login")
    private LocalDateTime lastLogin;

    @Column(name = "pin_code")
    private String pinCode; // Quick login PIN for POS terminals

    @OneToMany(mappedBy = "cashier", cascade = CascadeType.ALL)
    @Builder.Default
    @JsonIgnore
    private Set<CashierSession> cashierSessions = new HashSet<>();

    @OneToMany(mappedBy = "cashier")
    @Builder.Default
    @JsonIgnore
    private Set<Order> processedOrders = new HashSet<>();

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

    public String getFullName() {
        return firstName + " " + lastName;
    }

    public boolean canAccessShop(Shop shop) {
        return this.assignedShop != null && this.assignedShop.equals(shop);
    }

    public boolean isManager() {
        return this.role == CashierRole.MANAGER || this.role == CashierRole.ADMIN;
    }
}