package com.pos_onlineshop.hybrid.assetDisposal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AssetDisposalRepository extends JpaRepository<AssetDisposal, Long> {
    List<AssetDisposal> findAllByOrderByIdDesc();
}
