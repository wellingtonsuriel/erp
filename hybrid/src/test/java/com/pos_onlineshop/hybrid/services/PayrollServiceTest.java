package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.account.Account;
import com.pos_onlineshop.hybrid.account.AccountRepository;
import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.currency.CurrencyRepository;
import com.pos_onlineshop.hybrid.deductionType.DeductionType;
import com.pos_onlineshop.hybrid.deductionType.DeductionTypeRepository;
import com.pos_onlineshop.hybrid.dtos.PayrollRunResponse;
import com.pos_onlineshop.hybrid.dtos.ProcessPayrollRequest;
import com.pos_onlineshop.hybrid.employee.Employee;
import com.pos_onlineshop.hybrid.employee.EmployeeRepository;
import com.pos_onlineshop.hybrid.enums.AccountType;
import com.pos_onlineshop.hybrid.enums.DebitCredit;
import com.pos_onlineshop.hybrid.enums.PaymentMethod;
import com.pos_onlineshop.hybrid.gl.ManualLineSpec;
import com.pos_onlineshop.hybrid.journalEntry.JournalEntry;
import com.pos_onlineshop.hybrid.payrollRun.PayrollRun;
import com.pos_onlineshop.hybrid.payrollRun.PayrollRunRepository;
import com.pos_onlineshop.hybrid.payslip.Payslip;
import com.pos_onlineshop.hybrid.payslip.PayslipRepository;
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
class PayrollServiceTest {

    @Mock private PayrollRunRepository payrollRunRepository;
    @Mock private PayslipRepository payslipRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private DeductionTypeRepository deductionTypeRepository;
    @Mock private CurrencyRepository currencyRepository;
    @Mock private UserAccountRepository userAccountRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private GLPostingService glPostingService;

    private PayrollService service;
    private Currency usd;
    private UserAccount admin;
    private Account salaryExpense;
    private Account payrollPayable;
    private Account deductionsPayable;
    private Account cash;
    private Account bank;

    @BeforeEach
    void setUp() {
        service = new PayrollService(payrollRunRepository, payslipRepository, employeeRepository,
                deductionTypeRepository, currencyRepository, userAccountRepository, accountRepository, glPostingService);

        usd = Currency.builder().id(1L).code("USD").build();
        admin = UserAccount.builder().id(1L).username("admin1").password("x").email("admin1@test.com").build();
        salaryExpense = Account.builder().id(1L).code("5200").name("Salary and Wages Expense")
                .accountType(AccountType.EXPENSE).normalBalance(DebitCredit.DEBIT).active(true).build();
        payrollPayable = Account.builder().id(2L).code("2400").name("Payroll Payable")
                .accountType(AccountType.LIABILITY).normalBalance(DebitCredit.CREDIT).active(true).build();
        deductionsPayable = Account.builder().id(3L).code("2410").name("Payroll Deductions Payable")
                .accountType(AccountType.LIABILITY).normalBalance(DebitCredit.CREDIT).active(true).build();
        cash = Account.builder().id(4L).code("1010").name("Cash on Hand")
                .accountType(AccountType.ASSET).normalBalance(DebitCredit.DEBIT).active(true).build();
        bank = Account.builder().id(5L).code("1030").name("Bank")
                .accountType(AccountType.ASSET).normalBalance(DebitCredit.DEBIT).active(true).build();

        lenient().when(currencyRepository.findById(1L)).thenReturn(Optional.of(usd));
        lenient().when(userAccountRepository.findById(1L)).thenReturn(Optional.of(admin));
        lenient().when(accountRepository.findByCode("5200")).thenReturn(Optional.of(salaryExpense));
        lenient().when(accountRepository.findByCode("2400")).thenReturn(Optional.of(payrollPayable));
        lenient().when(accountRepository.findByCode("2410")).thenReturn(Optional.of(deductionsPayable));
        lenient().when(accountRepository.findByCode("1010")).thenReturn(Optional.of(cash));
        lenient().when(accountRepository.findByCode("1030")).thenReturn(Optional.of(bank));
        lenient().when(payrollRunRepository.save(any(PayrollRun.class))).thenAnswer(inv -> {
            PayrollRun run = inv.getArgument(0);
            if (run.getId() == null) run.setId(90L);
            return run;
        });
        lenient().when(payslipRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
    }

    private Employee employee(long id, String number, String name, String salary) {
        return Employee.builder().id(id).employeeNumber(number).fullName(name).active(true)
                .baseSalary(new BigDecimal(salary)).salaryCurrency(usd).build();
    }

    private ProcessPayrollRequest request() {
        ProcessPayrollRequest request = new ProcessPayrollRequest();
        request.setRunNumber("PAY-2026-08");
        request.setPeriodStart(LocalDate.of(2026, 8, 1));
        request.setPeriodEnd(LocalDate.of(2026, 8, 31));
        request.setPayDate(LocalDate.of(2026, 8, 31));
        request.setCurrencyId(1L);
        request.setPaymentMethod(PaymentMethod.CASH);
        request.setUserId(1L);
        return request;
    }

    @Test
    void processPayrollRejectsADuplicateRunNumber() {
        when(payrollRunRepository.existsByRunNumber("PAY-2026-08")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> service.processPayroll(request()));
    }

    @Test
    void processPayrollRejectsWhenNoEmployeesAreEligible() {
        when(payrollRunRepository.existsByRunNumber("PAY-2026-08")).thenReturn(false);
        when(employeeRepository.findByActiveTrue()).thenReturn(List.of());

        assertThrows(IllegalArgumentException.class, () -> service.processPayroll(request()));
    }

    @Test
    void processPayrollComputesTotalsAndPostsABalancedAccrualEntry() {
        when(payrollRunRepository.existsByRunNumber("PAY-2026-08")).thenReturn(false);
        Employee jane = employee(1L, "EMP-1", "Jane Doe", "1000.00");
        Employee john = employee(2L, "EMP-2", "John Smith", "2000.00");
        when(employeeRepository.findByActiveTrue()).thenReturn(List.of(jane, john));
        DeductionType tax = DeductionType.builder().id(1L).name("Tax").percentage(true)
                .rate(new BigDecimal("10")).active(true).build();
        when(deductionTypeRepository.findByActiveTrue()).thenReturn(List.of(tax));

        JournalEntry entry = JournalEntry.builder().id(700L).entryNumber(70L).build();
        ArgumentCaptor<List<ManualLineSpec>> captor = ArgumentCaptor.forClass(List.class);
        when(glPostingService.postManual(eq("PAYROLL-ACCRUAL-PAY-2026-08"), eq(LocalDate.of(2026, 8, 31)), anyString(),
                any(), eq("PAYROLL_RUN"), eq(90L), captor.capture(), eq("admin1")))
                .thenReturn(entry);

        PayrollRunResponse response = service.processPayroll(request());

        assertEquals("PROCESSED", response.getStatus());
        assertEquals(0, new BigDecimal("3000.00").compareTo(response.getTotalGrossPay()));
        assertEquals(0, new BigDecimal("300.0000").compareTo(response.getTotalDeductions()));
        assertEquals(0, new BigDecimal("2700.0000").compareTo(response.getTotalNetPay()));
        assertEquals(2, response.getPayslips().size());

        List<ManualLineSpec> specs = captor.getValue();
        BigDecimal totalDebits = specs.stream().map(ManualLineSpec::debitAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCredits = specs.stream().map(ManualLineSpec::creditAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, totalDebits.compareTo(totalCredits));
        assertTrue(specs.stream().anyMatch(s -> s.account() == salaryExpense && s.debitAmount().compareTo(BigDecimal.ZERO) > 0));
        assertTrue(specs.stream().anyMatch(s -> s.account() == payrollPayable && s.creditAmount().compareTo(BigDecimal.ZERO) > 0));
        assertTrue(specs.stream().anyMatch(s -> s.account() == deductionsPayable && s.creditAmount().compareTo(BigDecimal.ZERO) > 0));
    }

    @Test
    void processPayrollRejectsAnEmployeeWhosSalaryCurrencyDoesNotMatchTheRun() {
        when(payrollRunRepository.existsByRunNumber("PAY-2026-08")).thenReturn(false);
        Currency zwg = Currency.builder().id(2L).code("ZWG").build();
        Employee mismatched = employee(1L, "EMP-1", "Jane Doe", "1000.00");
        mismatched.setSalaryCurrency(zwg);
        when(employeeRepository.findByActiveTrue()).thenReturn(List.of(mismatched));

        assertThrows(IllegalArgumentException.class, () -> service.processPayroll(request()));
    }

    private PayrollRun processedRun() {
        return PayrollRun.builder().id(90L).runNumber("PAY-2026-08")
                .periodStart(LocalDate.of(2026, 8, 1)).periodEnd(LocalDate.of(2026, 8, 31)).payDate(LocalDate.of(2026, 8, 31))
                .currency(usd).totalGrossPay(new BigDecimal("3000.00")).totalDeductions(new BigDecimal("300.00"))
                .totalNetPay(new BigDecimal("2700.00")).paymentMethod(PaymentMethod.CASH).createdBy(admin)
                .accrualJournalEntry(JournalEntry.builder().id(700L).entryNumber(70L).build()).build();
    }

    @Test
    void payRunPostsTheNetPaymentAndMarksTheRunPaid() {
        PayrollRun run = processedRun();
        when(payrollRunRepository.findById(90L)).thenReturn(Optional.of(run));
        when(payslipRepository.findByPayrollRun(run)).thenReturn(List.of());
        JournalEntry paymentEntry = JournalEntry.builder().id(701L).entryNumber(71L).build();
        ArgumentCaptor<List<ManualLineSpec>> captor = ArgumentCaptor.forClass(List.class);
        when(glPostingService.postManual(eq("PAYROLL-PAYMENT-PAY-2026-08"), any(LocalDate.class), anyString(),
                any(), eq("PAYROLL_RUN"), eq(90L), captor.capture(), eq("admin1")))
                .thenReturn(paymentEntry);

        PayrollRunResponse response = service.payRun(90L, 1L);

        assertEquals("PAID", response.getStatus());
        List<ManualLineSpec> specs = captor.getValue();
        assertEquals(2, specs.size());
        ManualLineSpec cashLine = specs.stream().filter(s -> s.account() == cash).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("2700.00").compareTo(cashLine.creditAmount()));
        ManualLineSpec payableLine = specs.stream().filter(s -> s.account() == payrollPayable).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("2700.00").compareTo(payableLine.debitAmount()));
    }

    @Test
    void payRunRejectsARunThatIsAlreadyPaid() {
        PayrollRun run = processedRun();
        run.setStatus(com.pos_onlineshop.hybrid.enums.PayrollRunStatus.PAID);
        when(payrollRunRepository.findById(90L)).thenReturn(Optional.of(run));

        assertThrows(IllegalStateException.class, () -> service.payRun(90L, 1L));
        verifyNoInteractions(glPostingService);
    }
}
