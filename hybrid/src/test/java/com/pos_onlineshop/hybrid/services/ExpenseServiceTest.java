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
import com.pos_onlineshop.hybrid.enums.AccountType;
import com.pos_onlineshop.hybrid.enums.DebitCredit;
import com.pos_onlineshop.hybrid.enums.ExpensePayeeType;
import com.pos_onlineshop.hybrid.enums.ExpenseStatus;
import com.pos_onlineshop.hybrid.enums.GLSourceModule;
import com.pos_onlineshop.hybrid.enums.PaymentMethod;
import com.pos_onlineshop.hybrid.expense.Expense;
import com.pos_onlineshop.hybrid.expense.ExpenseRepository;
import com.pos_onlineshop.hybrid.expenseCategory.ExpenseCategory;
import com.pos_onlineshop.hybrid.expenseCategory.ExpenseCategoryRepository;
import com.pos_onlineshop.hybrid.gl.ManualLineSpec;
import com.pos_onlineshop.hybrid.journalEntry.JournalEntry;
import com.pos_onlineshop.hybrid.shop.ShopRepository;
import com.pos_onlineshop.hybrid.suppliers.SuppliersRepository;
import com.pos_onlineshop.hybrid.userAccount.UserAccount;
import com.pos_onlineshop.hybrid.userAccount.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExpenseServiceTest {

    @Mock private ExpenseRepository expenseRepository;
    @Mock private ExpenseCategoryRepository expenseCategoryRepository;
    @Mock private SuppliersRepository suppliersRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private CurrencyRepository currencyRepository;
    @Mock private ShopRepository shopRepository;
    @Mock private UserAccountRepository userAccountRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private GLPostingService glPostingService;

    private ExpenseService service;
    private Currency currency;
    private ExpenseCategory category;
    private UserAccount preparer;
    private UserAccount approver;
    private Account operatingExpenses;
    private Account cash;
    private Account bank;
    private Account vatInput;

    @BeforeEach
    void setUp() {
        service = new ExpenseService(expenseRepository, expenseCategoryRepository, suppliersRepository,
                employeeRepository, currencyRepository, shopRepository, userAccountRepository, accountRepository, glPostingService);

        currency = Currency.builder().id(1L).code("USD").build();
        category = ExpenseCategory.builder().id(1L).name("Travel").glAccountCode("5300").active(true).build();
        preparer = UserAccount.builder().id(1L).username("clerk1").password("x").email("clerk1@test.com").build();
        approver = UserAccount.builder().id(2L).username("manager1").password("x").email("manager1@test.com").build();
        operatingExpenses = Account.builder().id(1L).code("5300").name("Operating Expenses")
                .accountType(AccountType.EXPENSE).normalBalance(DebitCredit.DEBIT).active(true).build();
        cash = Account.builder().id(2L).code("1010").name("Cash on Hand")
                .accountType(AccountType.ASSET).normalBalance(DebitCredit.DEBIT).active(true).build();
        bank = Account.builder().id(3L).code("1030").name("Bank")
                .accountType(AccountType.ASSET).normalBalance(DebitCredit.DEBIT).active(true).build();
        vatInput = Account.builder().id(4L).code("1400").name("VAT Input / Recoverable")
                .accountType(AccountType.ASSET).normalBalance(DebitCredit.DEBIT).active(true).build();

        lenient().when(currencyRepository.findById(1L)).thenReturn(Optional.of(currency));
        lenient().when(expenseCategoryRepository.findById(1L)).thenReturn(Optional.of(category));
        lenient().when(userAccountRepository.findById(1L)).thenReturn(Optional.of(preparer));
        lenient().when(userAccountRepository.findById(2L)).thenReturn(Optional.of(approver));
        lenient().when(accountRepository.findByCode("5300")).thenReturn(Optional.of(operatingExpenses));
        lenient().when(accountRepository.findByCode("1010")).thenReturn(Optional.of(cash));
        lenient().when(accountRepository.findByCode("1030")).thenReturn(Optional.of(bank));
        lenient().when(accountRepository.findByCode("1400")).thenReturn(Optional.of(vatInput));
        lenient().when(expenseRepository.save(any(Expense.class))).thenAnswer(inv -> {
            Expense e = inv.getArgument(0);
            if (e.getId() == null) e.setId(20L);
            return e;
        });
    }

    private CreateExpenseRequest request(ExpensePayeeType payeeType) {
        CreateExpenseRequest request = new CreateExpenseRequest();
        request.setExpenseNumber("EXP-1");
        request.setCategoryId(1L);
        request.setDescription("Fuel for delivery van");
        request.setPayeeType(payeeType);
        request.setCurrencyId(1L);
        request.setAmount(new BigDecimal("100.00"));
        request.setTaxAmount(new BigDecimal("15.00"));
        request.setExpenseDate(LocalDate.of(2026, 8, 15));
        request.setPaymentMethod(PaymentMethod.CASH);
        request.setCreatedByUserId(1L);
        return request;
    }

    @Test
    void createExpenseRejectsADuplicateNumber() {
        when(expenseRepository.existsByExpenseNumber("EXP-1")).thenReturn(true);
        CreateExpenseRequest request = request(ExpensePayeeType.OTHER);
        request.setPayeeName("Fuel Station");

        assertThrows(IllegalArgumentException.class, () -> service.createExpense(request));
    }

    @Test
    void createExpenseRequiresAnEmployeeWhenPayeeTypeIsEmployee() {
        when(expenseRepository.existsByExpenseNumber("EXP-1")).thenReturn(false);
        CreateExpenseRequest request = request(ExpensePayeeType.EMPLOYEE);

        assertThrows(IllegalArgumentException.class, () -> service.createExpense(request));
    }

    @Test
    void createExpenseSucceedsForAnEmployeeReimbursement() {
        when(expenseRepository.existsByExpenseNumber("EXP-1")).thenReturn(false);
        Employee employee = Employee.builder().id(5L).employeeNumber("EMP-1").fullName("Jane Doe").active(true).build();
        when(employeeRepository.findById(5L)).thenReturn(Optional.of(employee));
        CreateExpenseRequest request = request(ExpensePayeeType.EMPLOYEE);
        request.setEmployeeId(5L);

        ExpenseResponse response = service.createExpense(request);

        assertEquals("DRAFT", response.getStatus());
        assertEquals("Jane Doe", response.getEmployeeName());
        assertEquals(0, new BigDecimal("115.00").compareTo(response.getTotalAmount()));
    }

    @Test
    void createExpenseRequiresAPayeeNameWhenPayeeTypeIsOther() {
        when(expenseRepository.existsByExpenseNumber("EXP-1")).thenReturn(false);
        CreateExpenseRequest request = request(ExpensePayeeType.OTHER);

        assertThrows(IllegalArgumentException.class, () -> service.createExpense(request));
    }

    private Expense submittedExpense() {
        return Expense.builder().id(20L).expenseNumber("EXP-1").category(category)
                .description("Fuel for delivery van").payeeType(ExpensePayeeType.OTHER).payeeName("Fuel Station")
                .currency(currency).amount(new BigDecimal("100.00")).taxAmount(new BigDecimal("15.00"))
                .expenseDate(LocalDate.of(2026, 8, 15)).paymentMethod(PaymentMethod.CASH)
                .status(ExpenseStatus.SUBMITTED).createdBy(preparer).build();
    }

    @Test
    void approveAndPayPostsExpenseAndTaxLinesAgainstCash() {
        Expense expense = submittedExpense();
        when(expenseRepository.findById(20L)).thenReturn(Optional.of(expense));
        JournalEntry entry = JournalEntry.builder().id(500L).entryNumber(50L).build();
        ArgumentCaptor<List<ManualLineSpec>> captor = ArgumentCaptor.forClass(List.class);
        when(glPostingService.postManual(eq("EXPENSE-20"), eq(LocalDate.of(2026, 8, 15)), anyString(),
                eq(GLSourceModule.SYSTEM), eq("EXPENSE"), eq(20L), captor.capture(), eq("manager1")))
                .thenReturn(entry);

        ManualJournalActionRequest request = new ManualJournalActionRequest();
        request.setUserId(2L);
        ExpenseResponse response = service.approveAndPay(20L, request);

        assertEquals("PAID", response.getStatus());
        List<ManualLineSpec> specs = captor.getValue();
        assertEquals(3, specs.size());
        assertTrue(specs.stream().anyMatch(s -> s.account() == operatingExpenses && s.debitAmount().compareTo(BigDecimal.ZERO) > 0));
        assertTrue(specs.stream().anyMatch(s -> s.account() == vatInput && s.debitAmount().compareTo(BigDecimal.ZERO) > 0));
        ManualLineSpec cashLine = specs.stream().filter(s -> s.account() == cash).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("115.00").compareTo(cashLine.creditAmount()));
    }

    @Test
    void approveAndPayRejectsThePreparerApprovingTheirOwnExpense() {
        Expense expense = submittedExpense();
        when(expenseRepository.findById(20L)).thenReturn(Optional.of(expense));
        when(glPostingService.postManual(anyString(), any(LocalDate.class), anyString(), any(), anyString(), anyLong(), anyList(), anyString()))
                .thenReturn(JournalEntry.builder().id(500L).entryNumber(50L).build());

        ManualJournalActionRequest request = new ManualJournalActionRequest();
        request.setUserId(1L); // the preparer, not a different approver

        assertThrows(IllegalStateException.class, () -> service.approveAndPay(20L, request));
    }

    @Test
    void rejectMarksTheExpenseRejectedWithAReason() {
        Expense expense = submittedExpense();
        when(expenseRepository.findById(20L)).thenReturn(Optional.of(expense));

        RejectManualJournalRequest request = new RejectManualJournalRequest();
        request.setUserId(2L);
        request.setReason("Missing receipt");
        ExpenseResponse response = service.reject(20L, request);

        assertEquals("REJECTED", response.getStatus());
        assertEquals("Missing receipt", response.getRejectionReason());
        verifyNoInteractions(glPostingService);
    }
}
