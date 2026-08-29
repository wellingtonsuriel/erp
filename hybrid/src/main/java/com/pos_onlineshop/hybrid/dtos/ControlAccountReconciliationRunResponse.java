package com.pos_onlineshop.hybrid.dtos;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ControlAccountReconciliationRunResponse {
    private Long id;
    private LocalDate asOfDate;
    private String performedBy;
    private LocalDateTime createdAt;
    private List<ControlAccountReconciliationLineResponse> lines;
}
