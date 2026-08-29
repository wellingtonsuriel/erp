package com.pos_onlineshop.hybrid.dtos;

import com.pos_onlineshop.hybrid.enums.ExpensePayeeType;
import com.pos_onlineshop.hybrid.enums.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class CreateExpenseRequest {

    @NotBlank(message = "Expense number is required")
    private String expenseNumber;

    @NotNull(message = "Category is required")
    private Long categoryId;

    @NotBlank(message = "Description is required")
    private String description;

    @NotNull(message = "Payee type is required")
    private ExpensePayeeType payeeType;

    /** Required when payeeType is SUPPLIER. */
    private Long supplierId;

    /** Required when payeeType is EMPLOYEE. */
    private Long employeeId;

    /** Required when payeeType is OTHER; otherwise an optional display override. */
    private String payeeName;

    @NotNull(message = "Currency is required")
    private Long currencyId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.0001", message = "Amount must be positive")
    private BigDecimal amount;

    @DecimalMin(value = "0.00", message = "Tax amount cannot be negative")
    private BigDecimal taxAmount;

    @NotNull(message = "Expense date is required")
    private LocalDate expenseDate;

    @NotNull(message = "Payment method is required")
    private PaymentMethod paymentMethod;

    private Long shopId;

    private String attachmentReference;

    @NotNull(message = "Creator is required")
    private Long createdByUserId;
}
