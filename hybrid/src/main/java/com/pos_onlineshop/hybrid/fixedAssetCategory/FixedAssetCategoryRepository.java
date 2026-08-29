package com.pos_onlineshop.hybrid.fixedAssetCategory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FixedAssetCategoryRepository extends JpaRepository<FixedAssetCategory, Long> {
    boolean existsByName(String name);

    Optional<FixedAssetCategory> findByName(String name);

    List<FixedAssetCategory> findByActiveTrue();
}
