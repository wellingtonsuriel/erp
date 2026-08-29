package com.pos_onlineshop.hybrid.dtos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class CreateSalesReturnRequest {

    @NotBlank(message = "Return number is required")
    private String returnNumber;

    @NotNull(message = "Order is required")
    private Long orderId;

    @NotBlank(message = "Reason is required")
    private String reason;

    @NotNull(message = "Return date is required")
    private LocalDate returnDate;

    @NotEmpty(message = "At least one return line is required")
    @Valid
    private List<SalesReturnLineRequest> lines;

    @NotNull(message = "Creator is required")
    private Long createdByUserId;
}
