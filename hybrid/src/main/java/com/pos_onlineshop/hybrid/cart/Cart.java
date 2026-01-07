package com.pos_onlineshop.hybrid.cart;

import com.pos_onlineshop.hybrid.cartItem.CartItem;
import com.pos_onlineshop.hybrid.userAccount.UserAccount;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "carts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"cartItems"})
@ToString(exclude = {"cartItems"})
public class Cart {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<CartItem> cartItems = new ArrayList<>();

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    public void addItem(CartItem item) {
        cartItems.add(item);
        item.setCart(this);
        this.updatedAt = LocalDateTime.now();
    }

    public void removeItem(CartItem item) {
        cartItems.remove(item);
        item.setCart(null);
        this.updatedAt = LocalDateTime.now();
    }

    public void clear() {
        cartItems.clear();
        this.updatedAt = LocalDateTime.now();
    }

    public BigDecimal getTotalAmount() {
        return cartItems.stream()
                .map(item -> item.getProduct().getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public int getTotalItems() {
        return cartItems.stream()
                .mapToInt(CartItem::getQuantity)
                .sum();
    }
}
