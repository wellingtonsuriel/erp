package com.pos_onlineshop.hybrid.dtos;

import com.pos_onlineshop.hybrid.enums.DepreciationMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class CreateFixedAssetRequest {

    @NotBlank(message = "Asset number is required")
    private String assetNumber;

    @NotBlank(message = "Name is required")
    private String name;

    @NotNull(message = "Category is required")
    private Long categoryId;

    private Long shopId;

    @NotNull(message = "Acquisition date is required")
    private LocalDate acquisitionDate;

    @NotNull(message = "Acquisition cost is required")
    @DecimalMin(value = "0.0001", message = "Acquisition cost must be positive")
    private BigDecimal acquisitionCost;

    @NotNull(message = "Useful life is required")
    @Min(value = 1, message = "Useful life must be at least 1 month")
    private Integer usefulLifeMonths;

    @DecimalMin(value = "0.00", message = "Residual value cannot be negative")
    private BigDecimal residualValue;

    private DepreciationMethod depreciationMethod;
}
