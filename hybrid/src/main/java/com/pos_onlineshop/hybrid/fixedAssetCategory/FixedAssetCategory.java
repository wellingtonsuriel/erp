package com.pos_onlineshop.hybrid.fixedAssetCategory;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "fixed_asset_categories")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FixedAssetCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(length = 300)
    private String description;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}
