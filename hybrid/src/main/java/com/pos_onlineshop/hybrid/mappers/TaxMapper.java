package com.pos_onlineshop.hybrid.mappers;

import com.pos_onlineshop.hybrid.dtos.TaxResponse;
import com.pos_onlineshop.hybrid.tax.Tax;
import org.springframework.stereotype.Component;

/**
 * Mapper component for converting Tax entities to DTOs.
 */
@Component
public class TaxMapper {

    /**
     * Convert Tax entity to TaxResponse DTO.
     *
     * @param tax the Tax entity
     * @return the TaxResponse DTO
     */
    public TaxResponse toResponse(Tax tax) {
        if (tax == null) {
            return null;
        }

        return TaxResponse.builder()
                .taxId(tax.getTaxId())
                .taxNature(tax.getTaxNature())
                .taxName(tax.getTaxName())
                .taxCalculationType(tax.getTaxCalculationType())
                .taxValue(tax.getTaxValue())
                .currencyId(tax.getCurrency() != null ? tax.getCurrency().getId() : null)
                .currencyCode(tax.getCurrency() != null ? tax.getCurrency().getCode() : null)
                .currencySymbol(tax.getCurrency() != null ? tax.getCurrency().getSymbol() : null)
                .active(tax.getActive())
                .createdAt(tax.getCreatedAt())
                .updatedAt(tax.getUpdatedAt())
                .build();
    }
}
