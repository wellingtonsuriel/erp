package com.pos_onlineshop.hybrid.ias29Restatement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface Ias29RestatementEntryRepository extends JpaRepository<Ias29RestatementEntry, Long> {
    List<Ias29RestatementEntry> findAllByOrderByIdDesc();

    List<Ias29RestatementEntry> findByFixedAssetIdOrderByIdDesc(Long fixedAssetId);
}
