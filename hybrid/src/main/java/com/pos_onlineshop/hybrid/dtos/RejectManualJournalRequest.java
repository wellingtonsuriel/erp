package com.pos_onlineshop.hybrid.dtos;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RejectManualJournalRequest {

    @NotBlank(message = "A reason is required to reject a manual journal")
    private String reason;
}
