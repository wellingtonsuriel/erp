package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.deductionType.DeductionType;
import com.pos_onlineshop.hybrid.deductionType.DeductionTypeRepository;
import com.pos_onlineshop.hybrid.dtos.CreateDeductionTypeRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class DeductionTypeService {

    private final DeductionTypeRepository deductionTypeRepository;

    @Transactional(readOnly = true)
    public List<DeductionType> findAllActive() {
        return deductionTypeRepository.findByActiveTrue();
    }

    public DeductionType create(CreateDeductionTypeRequest request) {
        if (deductionTypeRepository.existsByName(request.getName())) {
            throw new IllegalArgumentException("A deduction type named '" + request.getName() + "' already exists");
        }
        boolean percentage = Boolean.TRUE.equals(request.getPercentage());
        if (percentage && request.getRate() == null) {
            throw new IllegalArgumentException("Rate is required when percentage is true");
        }
        if (!percentage && request.getFixedAmount() == null) {
            throw new IllegalArgumentException("Fixed amount is required when percentage is false");
        }
        return deductionTypeRepository.save(DeductionType.builder()
                .name(request.getName())
                .percentage(percentage)
                .rate(request.getRate())
                .fixedAmount(request.getFixedAmount())
                .build());
    }

    public DeductionType deactivate(Long id) {
        DeductionType deductionType = deductionTypeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Deduction type not found: " + id));
        deductionType.setActive(false);
        return deductionTypeRepository.save(deductionType);
    }
}
