package com.pos_onlineshop.hybrid.dtos;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateExpenseCategoryRequest {

    @NotBlank(message = "Name is required")
    private String name;

    /** Defaults to 5300 Operating Expenses if omitted. */
    private String glAccountCode;
}
