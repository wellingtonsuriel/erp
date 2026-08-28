package com.pos_onlineshop.hybrid.dtos;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Body for submit/approve/post - the acting user must always be identified explicitly
 * since there is no authenticated-principal context wired into these controllers yet. */
@Data
public class ManualJournalActionRequest {

    @NotNull(message = "Acting user is required")
    private Long userId;
}
