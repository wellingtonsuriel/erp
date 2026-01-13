package com.pos_onlineshop.hybrid.dtos;

import com.pos_onlineshop.hybrid.enums.TaxCalculationType;
import com.pos_onlineshop.hybrid.enums.TaxNature;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO for tax response.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaxResponse {

    private Long taxId;

    private TaxNature taxNature;

    private String taxName;

    private TaxCalculationType taxCalculationType;

    private BigDecimal taxValue;

    // Currency details (for FIXED taxes)
    private Long currencyId;
    private String currencyCode;
    private String currencySymbol;

    private Boolean active;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
