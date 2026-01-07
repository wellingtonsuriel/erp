package com.pos_onlineshop.hybrid.products;

import com.pos_onlineshop.hybrid.currency.Currency;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Entity
@Table(name = "products")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"productPrices"})
@ToString(exclude = {"productPrices"})
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false)
    private String category;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "is_weighable")
    @Builder.Default
    private boolean weighable = false;

    @Column(name = "is_active")
    @Builder.Default
    private boolean active = true;


    @Column(name = "max_stock")
    private Integer maxStock; // Maximum stock this shop can hold for this product

    @Column(name = "min_stock")
    @Builder.Default
    private Integer minStock = 0; // Minimum stock to maintain

    @Column(name = "weight", precision = 10, scale = 3)
    private BigDecimal weight; // For weighable products

    @Column(name = "unit_of_measure")
    private String unitOfMeasure; // kg, piece, liter, etc.


    @Column(name = "actual_measure")
    private String actualMeasure; // kg, piece, liter, etc.


    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Version
    private Long version; // For optimistic locking

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // Business methods


}