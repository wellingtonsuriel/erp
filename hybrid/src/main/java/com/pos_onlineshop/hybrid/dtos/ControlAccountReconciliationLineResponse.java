package com.pos_onlineshop.hybrid.dtos;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class ControlAccountReconciliationLineResponse {
    private Long id;
    private Long runId;
    private String accountCode;
    private String accountName;
    private String subledgerName;
    private BigDecimal glBalance;
    private BigDecimal subledgerBalance;
    private BigDecimal variance;
    private boolean matched;
    private String note;
    private boolean resolved;
    private String resolutionReason;
    private String resolvedBy;
    private LocalDateTime resolvedAt;
}
