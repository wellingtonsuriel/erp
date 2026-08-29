package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.account.Account;
import com.pos_onlineshop.hybrid.account.AccountRepository;
import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.currency.CurrencyRepository;
import com.pos_onlineshop.hybrid.dtos.CreateExpenseRequest;
import com.pos_onlineshop.hybrid.dtos.ExpenseResponse;
import com.pos_onlineshop.hybrid.dtos.ManualJournalActionRequest;
import com.pos_onlineshop.hybrid.dtos.RejectManualJournalRequest;
import com.pos_onlineshop.hybrid.employee.Employee;
import com.pos_onlineshop.hybrid.employee.EmployeeRepository;
import com.pos_onlineshop.hybrid.enums.ExpensePayeeType;
import com.pos_onlineshop.hybrid.enums.GLSourceModule;
import com.pos_onlineshop.hybrid.enums.PaymentMethod;
import com.pos_onlineshop.hybrid.expense.Expense;
import com.pos_onlineshop.hybrid.expense.ExpenseRepository;
import com.pos_onlineshop.hybrid.expenseCategory.ExpenseCategory;
import com.pos_onlineshop.hybrid.expenseCategory.ExpenseCategoryRepository;
import com.pos_onlineshop.hybrid.gl.ManualLineSpec;
import com.pos_onlineshop.hybrid.journalEntry.JournalEntry;
import com.pos_onlineshop.hybrid.shop.Shop;
import com.pos_onlineshop.hybrid.shop.ShopRepository;
import com.pos_onlineshop.hybrid.suppliers.Suppliers;
import com.pos_onlineshop.hybrid.suppliers.SuppliersRepository;
import com.pos_onlineshop.hybrid.userAccount.UserAccount;
import com.pos_onlineshop.hybrid.userAccount.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Expenses paid immediately once approved - see Expense's class comment for the scope
 * boundary against SupplierInvoice (on-account supplier spend stays there, not here).
 * DRAFT -> SUBMITTED -> PAID: approveAndPay() posts to the GL and marks the expense paid in
 * one atomic action, since there is no separate "approved but not yet paid" state for a
 * cash/bank expense - approval IS the payment decision here.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExpenseService {

    private static final String VAT_INPUT_ACCOUNT_CODE = "1400";

    private final ExpenseRepository expenseRepository;
    private final ExpenseCategoryRepository expenseCategoryRepository;
    private final SuppliersRepository suppliersRepository;
    private final EmployeeRepository employeeRepository;
    private final CurrencyRepository currencyRepository;
    private final ShopRepository shopRepository;
    private final UserAccountRepository userAccountRepository;
    private final AccountRepository accountRepository;
    private final GLPostingService glPostingService;

    @Transactional(readOnly = true)
    public List<ExpenseResponse> findAll() {
        return expenseRepository.findAllByOrderByIdDesc().stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ExpenseResponse findById(Long id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    public ExpenseResponse createExpense(CreateExpenseRequest request) {
        if (expenseRepository.existsByExpenseNumber(request.getExpenseNumber())) {
            throw new IllegalArgumentException("An expense with number " + request.getExpenseNumber() + " already exists");
        }
        ExpenseCategory category = expenseCategoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new IllegalArgumentException("Category not found: " + request.getCategoryId()));
        Currency currency = currencyRepository.findById(request.getCurrencyId())
                .orElseThrow(() -> new IllegalArgumentException("Currency not found: " + request.getCurrencyId()));
        UserAccount createdBy = resolveUser(request.getCreatedByUserId());

        Suppliers supplier = null;
        Employee employee = null;
        if (request.getPayeeType() == ExpensePayeeType.SUPPLIER) {
            if (request.getSupplierId() == null) {
                throw new IllegalArgumentException("Supplier is required when payee type is SUPPLIER");
            }
            supplier = suppliersRepository.findById(request.getSupplierId())
                    .orElseThrow(() -> new IllegalArgumentException("Supplier not found: " + request.getSupplierId()));
        } else if (request.getPayeeType() == ExpensePayeeType.EMPLOYEE) {
            if (request.getEmployeeId() == null) {
                throw new IllegalArgumentException("Employee is required when payee type is EMPLOYEE");
            }
            employee = employeeRepository.findById(request.getEmployeeId())
                    .orElseThrow(() -> new IllegalArgumentException("Employee not found: " + request.getEmployeeId()));
        } else if (request.getPayeeName() == null || request.getPayeeName().isBlank()) {
            throw new IllegalArgumentException("Payee name is required when payee type is OTHER");
        }

        Shop shop = null;
        if (request.getShopId() != null) {
            shop = shopRepository.findById(request.getShopId())
                    .orElseThrow(() -> new IllegalArgumentException("Shop not found: " + request.getShopId()));
        }

        Expense expense = Expense.builder()
                .expenseNumber(request.getExpenseNumber())
                .category(category)
                .description(request.getDescription())
                .payeeType(request.getPayeeType())
                .supplier(supplier)
                .employee(employee)
                .payeeName(request.getPayeeName())
                .currency(currency)
                .amount(request.getAmount())
                .taxAmount(request.getTaxAmount() != null ? request.getTaxAmount() : BigDecimal.ZERO)
                .expenseDate(request.getExpenseDate())
                .paymentMethod(request.getPaymentMethod())
                .shop(shop)
                .attachmentReference(request.getAttachmentReference())
                .createdBy(createdBy)
                .build();

        Expense saved = expenseRepository.save(expense);
        log.info("Created expense {} for {}", saved.getExpenseNumber(), saved.getDescription());
        return toResponse(saved);
    }

    @Transactional
    public ExpenseResponse submit(Long id) {
        Expense expense = findOrThrow(id);
        expense.submit();
        return toResponse(expenseRepository.save(expense));
    }

    @Transactional
    public ExpenseResponse approveAndPay(Long id, ManualJournalActionRequest request) {
        Expense expense = findOrThrow(id);
        UserAccount approver = resolveUser(request.getUserId());

        JournalEntry entry = postToGeneralLedger(expense, approver.getUsername());
        expense.approveAndPay(approver, entry);

        Expense saved = expenseRepository.save(expense);
        log.info("Expense {} approved and paid - GL entry #{}", saved.getExpenseNumber(), entry.getEntryNumber());
        return toResponse(saved);
    }

    @Transactional
    public ExpenseResponse reject(Long id, RejectManualJournalRequest request) {
        Expense expense = findOrThrow(id);
        UserAccount approver = resolveUser(request.getUserId());
        expense.reject(approver, request.getReason());
        return toResponse(expenseRepository.save(expense));
    }

    private JournalEntry postToGeneralLedger(Expense expense, String postedBy) {
        Account expenseAccount = accountRepository.findByCode(expense.getCategory().getGlAccountCode())
                .orElseThrow(() -> new IllegalStateException("Chart of accounts is missing " + expense.getCategory().getGlAccountCode()));
        Account cashOrBank = accountRepository.findByCode(expense.getPaymentMethod() == PaymentMethod.CASH ? "1010" : "1030")
                .orElseThrow(() -> new IllegalStateException("Chart of accounts is missing the cash/bank account"));

        String memo = "Expense " + expense.getExpenseNumber() + " (" + expense.getDescription() + ")";
        Currency currency = expense.getCurrency();
        List<ManualLineSpec> specs = new ArrayList<>();
        specs.add(new ManualLineSpec(expenseAccount, expense.getAmount(), BigDecimal.ZERO, currency, BigDecimal.ONE, expense.getShop(), memo));
        if (expense.getTaxAmount().compareTo(BigDecimal.ZERO) > 0) {
            Account vatInput = accountRepository.findByCode(VAT_INPUT_ACCOUNT_CODE)
                    .orElseThrow(() -> new IllegalStateException("Chart of accounts is missing " + VAT_INPUT_ACCOUNT_CODE));
            specs.add(new ManualLineSpec(vatInput, expense.getTaxAmount(), BigDecimal.ZERO, currency, BigDecimal.ONE, expense.getShop(), memo));
        }
        specs.add(new ManualLineSpec(cashOrBank, BigDecimal.ZERO, expense.getTotalAmount(), currency, BigDecimal.ONE, expense.getShop(), memo));

        return glPostingService.postManual(
                "EXPENSE-" + expense.getId(), expense.getExpenseDate(), memo,
                GLSourceModule.SYSTEM, "EXPENSE", expense.getId(), specs, postedBy);
    }

    private UserAccount resolveUser(Long userId) {
        return userAccountRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
    }

    private Expense findOrThrow(Long id) {
        return expenseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Expense not found: " + id));
    }

    private ExpenseResponse toResponse(Expense expense) {
        return ExpenseResponse.builder()
                .id(expense.getId())
                .expenseNumber(expense.getExpenseNumber())
                .categoryId(expense.getCategory().getId())
                .categoryName(expense.getCategory().getName())
                .description(expense.getDescription())
                .payeeType(expense.getPayeeType().name())
                .supplierId(expense.getSupplier() != null ? expense.getSupplier().getId() : null)
                .supplierName(expense.getSupplier() != null ? expense.getSupplier().getName() : null)
                .employeeId(expense.getEmployee() != null ? expense.getEmployee().getId() : null)
                .employeeName(expense.getEmployee() != null ? expense.getEmployee().getFullName() : null)
                .payeeName(expense.getPayeeName())
                .currencyCode(expense.getCurrency() != null ? expense.getCurrency().getCode() : null)
                .amount(expense.getAmount())
                .taxAmount(expense.getTaxAmount())
                .totalAmount(expense.getTotalAmount())
                .expenseDate(expense.getExpenseDate())
                .paymentMethod(expense.getPaymentMethod().name())
                .attachmentReference(expense.getAttachmentReference())
                .status(expense.getStatus().name())
                .createdById(expense.getCreatedBy() != null ? expense.getCreatedBy().getId() : null)
                .createdByUsername(expense.getCreatedBy() != null ? expense.getCreatedBy().getUsername() : null)
                .createdAt(expense.getCreatedAt())
                .approvedById(expense.getApprovedBy() != null ? expense.getApprovedBy().getId() : null)
                .approvedByUsername(expense.getApprovedBy() != null ? expense.getApprovedBy().getUsername() : null)
                .approvedAt(expense.getApprovedAt())
                .rejectionReason(expense.getRejectionReason())
                .postedJournalEntryId(expense.getPostedJournalEntry() != null ? expense.getPostedJournalEntry().getId() : null)
                .postedJournalEntryNumber(expense.getPostedJournalEntry() != null ? expense.getPostedJournalEntry().getEntryNumber() : null)
                .build();
    }
}
