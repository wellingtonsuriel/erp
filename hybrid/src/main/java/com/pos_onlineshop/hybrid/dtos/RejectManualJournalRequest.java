package com.pos_onlineshop.hybrid.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RejectManualJournalRequest {

    @NotNull(message = "Acting user is required")
    private Long userId;

    @NotBlank(message = "A reason is required to reject a manual journal")
    private String reason;
}
