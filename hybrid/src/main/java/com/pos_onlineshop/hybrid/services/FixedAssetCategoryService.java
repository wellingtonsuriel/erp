package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.dtos.CreateFixedAssetCategoryRequest;
import com.pos_onlineshop.hybrid.fixedAssetCategory.FixedAssetCategory;
import com.pos_onlineshop.hybrid.fixedAssetCategory.FixedAssetCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class FixedAssetCategoryService {

    private final FixedAssetCategoryRepository fixedAssetCategoryRepository;

    @Transactional(readOnly = true)
    public List<FixedAssetCategory> findAllActive() {
        return fixedAssetCategoryRepository.findByActiveTrue();
    }

    public FixedAssetCategory create(CreateFixedAssetCategoryRequest request) {
        if (fixedAssetCategoryRepository.existsByName(request.getName())) {
            throw new IllegalArgumentException("A fixed asset category named '" + request.getName() + "' already exists");
        }
        return fixedAssetCategoryRepository.save(FixedAssetCategory.builder()
                .name(request.getName())
                .description(request.getDescription())
                .build());
    }
}
