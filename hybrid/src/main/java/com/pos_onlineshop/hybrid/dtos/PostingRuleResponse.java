package com.pos_onlineshop.hybrid.dtos;

import com.pos_onlineshop.hybrid.enums.FinancialEventType;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PostingRuleResponse {
    private Long id;
    private FinancialEventType eventType;
    private String description;
    private boolean active;
    private List<PostingRuleLineResponse> lines;
}
