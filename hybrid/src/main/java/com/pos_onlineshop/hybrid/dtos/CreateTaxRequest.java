package com.pos_onlineshop.hybrid.dtos;

import com.pos_onlineshop.hybrid.enums.TaxCalculationType;
import com.pos_onlineshop.hybrid.enums.TaxNature;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO for creating a new tax.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateTaxRequest {

    @NotNull(message = "Tax nature is required")
    private TaxNature taxNature;

    @NotBlank(message = "Tax name is required")
    private String taxName;

    @NotNull(message = "Tax calculation type is required")
    private TaxCalculationType taxCalculationType;

    @NotNull(message = "Tax value is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Tax value must be greater than 0")
    private BigDecimal taxValue;

    /**
     * Currency ID - required for FIXED taxes, optional for PERCENTAGE taxes.
     */
    private Long currencyId;

    @Builder.Default
    private Boolean active = true;
}
