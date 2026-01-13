package com.pos_onlineshop.hybrid.dtos;

import com.pos_onlineshop.hybrid.enums.TaxCalculationType;
import com.pos_onlineshop.hybrid.enums.TaxNature;
import jakarta.validation.constraints.DecimalMin;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO for updating an existing tax.
 * All fields are optional - only provided fields will be updated.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateTaxRequest {

    private TaxNature taxNature;

    private String taxName;

    private TaxCalculationType taxCalculationType;

    @DecimalMin(value = "0.0", inclusive = false, message = "Tax value must be greater than 0")
    private BigDecimal taxValue;

    /**
     * Currency ID - for FIXED taxes.
     */
    private Long currencyId;

    private Boolean active;
}
