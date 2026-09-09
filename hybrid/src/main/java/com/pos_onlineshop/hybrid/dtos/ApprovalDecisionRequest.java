package com.pos_onlineshop.hybrid.dtos;

import lombok.Data;

@Data
public class ApprovalDecisionRequest {

    // Ignored server-side: the deciding user is always the authenticated caller (see
    // WorkflowController/WorkflowService's class comments), never this request-body field.
    private Long userId;

    private String reason;
}
