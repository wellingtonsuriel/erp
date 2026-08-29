package com.pos_onlineshop.hybrid.dtos;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateFixedAssetCategoryRequest {

    @NotBlank(message = "Name is required")
    private String name;

    private String description;
}
