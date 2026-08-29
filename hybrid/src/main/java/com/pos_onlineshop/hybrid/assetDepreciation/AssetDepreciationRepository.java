package com.pos_onlineshop.hybrid.assetDepreciation;

import com.pos_onlineshop.hybrid.fixedAsset.FixedAsset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface AssetDepreciationRepository extends JpaRepository<AssetDepreciation, Long> {
    boolean existsByAssetAndPeriodDate(FixedAsset asset, LocalDate periodDate);

    List<AssetDepreciation> findByAssetOrderByPeriodDateDesc(FixedAsset asset);

    List<AssetDepreciation> findAllByOrderByIdDesc();
}
