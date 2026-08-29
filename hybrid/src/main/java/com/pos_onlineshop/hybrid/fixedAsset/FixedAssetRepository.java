package com.pos_onlineshop.hybrid.fixedAsset;

import com.pos_onlineshop.hybrid.enums.FixedAssetStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FixedAssetRepository extends JpaRepository<FixedAsset, Long> {
    boolean existsByAssetNumber(String assetNumber);

    List<FixedAsset> findAllByOrderByIdDesc();

    List<FixedAsset> findByStatus(FixedAssetStatus status);
}
