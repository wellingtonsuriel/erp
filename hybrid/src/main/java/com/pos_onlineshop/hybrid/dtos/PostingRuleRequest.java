package com.pos_onlineshop.hybrid.dtos;

import com.pos_onlineshop.hybrid.enums.FinancialEventType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class PostingRuleRequest {

    @NotNull(message = "Event type is required")
    private FinancialEventType eventType;

    private String description;

    private boolean active = true;

    @NotEmpty(message = "A posting rule needs at least one line")
    @Valid
    private List<PostingRuleLineRequest> lines;
}
