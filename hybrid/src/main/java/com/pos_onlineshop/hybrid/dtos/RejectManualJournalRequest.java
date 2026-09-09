package com.pos_onlineshop.hybrid.dtos;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RejectManualJournalRequest {

    // Ignored server-side: the rejecting user is always the authenticated caller (see
    // ManualJournalController/ManualJournalService's class comments), never this request-body field.
    private Long userId;

    @NotBlank(message = "A reason is required to reject a manual journal")
    private String reason;
}
