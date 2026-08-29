package com.pos_onlineshop.hybrid.dtos;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class ExpenseResponse {
    private Long id;
    private String expenseNumber;
    private Long categoryId;
    private String categoryName;
    private String description;
    private String payeeType;
    private Long supplierId;
    private String supplierName;
    private Long employeeId;
    private String employeeName;
    private String payeeName;
    private String currencyCode;
    private BigDecimal amount;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
    private LocalDate expenseDate;
    private String paymentMethod;
    private String attachmentReference;
    private String status;

    private Long createdById;
    private String createdByUsername;
    private LocalDateTime createdAt;

    private Long approvedById;
    private String approvedByUsername;
    private LocalDateTime approvedAt;
    private String rejectionReason;

    private Long postedJournalEntryId;
    private Long postedJournalEntryNumber;
}
