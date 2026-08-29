package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.account.Account;
import com.pos_onlineshop.hybrid.account.AccountRepository;
import com.pos_onlineshop.hybrid.currency.Currency;
import com.pos_onlineshop.hybrid.currency.CurrencyRepository;
import com.pos_onlineshop.hybrid.deductionType.DeductionType;
import com.pos_onlineshop.hybrid.deductionType.DeductionTypeRepository;
import com.pos_onlineshop.hybrid.dtos.PayrollRunResponse;
import com.pos_onlineshop.hybrid.dtos.PayslipResponse;
import com.pos_onlineshop.hybrid.dtos.ProcessPayrollRequest;
import com.pos_onlineshop.hybrid.employee.Employee;
import com.pos_onlineshop.hybrid.employee.EmployeeRepository;
import com.pos_onlineshop.hybrid.enums.GLSourceModule;
import com.pos_onlineshop.hybrid.enums.PayrollRunStatus;
import com.pos_onlineshop.hybrid.enums.PaymentMethod;
import com.pos_onlineshop.hybrid.gl.ManualLineSpec;
import com.pos_onlineshop.hybrid.journalEntry.JournalEntry;
import com.pos_onlineshop.hybrid.payrollRun.PayrollRun;
import com.pos_onlineshop.hybrid.payrollRun.PayrollRunRepository;
import com.pos_onlineshop.hybrid.payslip.Payslip;
import com.pos_onlineshop.hybrid.payslip.PayslipRepository;
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
 * Two-posting payroll mechanics: processPayroll() computes every payslip for the period and
 * posts the accrual atomically (Dr 5200 Salary Expense = Cr 2400 Payroll Payable [net] +
 * Cr 2410 Payroll Deductions Payable [total deductions]) - see PayrollRunStatus for why the
 * run is PROCESSED, not DRAFT, the instant this returns. payRun() posts the separate cash
 * payment (Dr 2400 / Cr Cash or Bank for the net only) once the money actually goes out.
 * Known limitation: a run has a single currency, and every payroll-eligible employee's
 * salaryCurrency must match it - true multi-currency payroll within one run isn't supported.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PayrollService {

    private static final String SALARY_EXPENSE_ACCOUNT_CODE = "5200";
    private static final String PAYROLL_PAYABLE_ACCOUNT_CODE = "2400";
    private static final String PAYROLL_DEDUCTIONS_PAYABLE_ACCOUNT_CODE = "2410";

    private final PayrollRunRepository payrollRunRepository;
    private final PayslipRepository payslipRepository;
    private final EmployeeRepository employeeRepository;
    private final DeductionTypeRepository deductionTypeRepository;
    private final CurrencyRepository currencyRepository;
    private final UserAccountRepository userAccountRepository;
    private final AccountRepository accountRepository;
    private final GLPostingService glPostingService;
    private final CurrencyService currencyService;

    @Transactional(readOnly = true)
    public List<PayrollRunResponse> findAll() {
        return payrollRunRepository.findAllByOrderByIdDesc().stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PayrollRunResponse findById(Long id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    public PayrollRunResponse processPayroll(ProcessPayrollRequest request) {
        if (payrollRunRepository.existsByRunNumber(request.getRunNumber())) {
            throw new IllegalArgumentException("A payroll run with number " + request.getRunNumber() + " already exists");
        }
        if (!request.getPeriodEnd().isAfter(request.getPeriodStart())
                && !request.getPeriodEnd().isEqual(request.getPeriodStart())) {
            throw new IllegalArgumentException("Period end cannot be before period start");
        }
        Currency currency = currencyRepository.findById(request.getCurrencyId())
                .orElseThrow(() -> new IllegalArgumentException("Currency not found: " + request.getCurrencyId()));
        UserAccount createdBy = resolveUser(request.getUserId());

        List<Employee> eligible = employeeRepository.findByActiveTrue().stream()
                .filter(Employee::isPayrollEligible)
                .collect(Collectors.toList());
        if (eligible.isEmpty()) {
            throw new IllegalArgumentException("No payroll-eligible employees found");
        }
        for (Employee employee : eligible) {
            if (!employee.getSalaryCurrency().getId().equals(currency.getId())) {
                throw new IllegalArgumentException("Employee " + employee.getEmployeeNumber()
                        + " is paid in " + employee.getSalaryCurrency().getCode()
                        + ", which does not match the run currency " + currency.getCode());
            }
        }

        List<DeductionType> activeDeductions = deductionTypeRepository.findByActiveTrue();

        PayrollRun run = PayrollRun.builder()
                .runNumber(request.getRunNumber())
                .periodStart(request.getPeriodStart())
                .periodEnd(request.getPeriodEnd())
                .payDate(request.getPayDate())
                .currency(currency)
                .totalGrossPay(BigDecimal.ZERO)
                .totalDeductions(BigDecimal.ZERO)
                .totalNetPay(BigDecimal.ZERO)
                .paymentMethod(request.getPaymentMethod())
                .createdBy(createdBy)
                .build();
        PayrollRun savedRun = payrollRunRepository.save(run);

        List<Payslip> payslips = new ArrayList<>();
        BigDecimal totalGross = BigDecimal.ZERO;
        BigDecimal totalDeductions = BigDecimal.ZERO;
        BigDecimal totalNet = BigDecimal.ZERO;
        for (Employee employee : eligible) {
            BigDecimal grossPay = employee.getBaseSalary();
            BigDecimal deductions = BigDecimal.ZERO;
            for (DeductionType deductionType : activeDeductions) {
                deductions = deductions.add(deductionType.calculate(grossPay));
            }
            BigDecimal netPay = grossPay.subtract(deductions);
            if (netPay.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalStateException("Deductions exceed gross pay for employee " + employee.getEmployeeNumber());
            }
            payslips.add(Payslip.builder()
                    .payrollRun(savedRun)
                    .employee(employee)
                    .currency(currency)
                    .grossPay(grossPay)
                    .totalDeductions(deductions)
                    .netPay(netPay)
                    .build());
            totalGross = totalGross.add(grossPay);
            totalDeductions = totalDeductions.add(deductions);
            totalNet = totalNet.add(netPay);
        }
        payslipRepository.saveAll(payslips);

        savedRun.setTotalGrossPay(totalGross);
        savedRun.setTotalDeductions(totalDeductions);
        savedRun.setTotalNetPay(totalNet);

        JournalEntry accrualEntry = postAccrualToGeneralLedger(savedRun);
        savedRun.setAccrualJournalEntry(accrualEntry);
        PayrollRun finalRun = payrollRunRepository.save(savedRun);

        log.info("Processed payroll run {} for {} employees - gross {} {}, GL entry #{}",
                finalRun.getRunNumber(), eligible.size(), totalGross, currency.getCode(), accrualEntry.getEntryNumber());
        return toResponse(finalRun, payslips);
    }

    @Transactional
    public PayrollRunResponse payRun(Long id, Long userId) {
        PayrollRun run = findOrThrow(id);
        if (!run.canBePaid()) {
            throw new IllegalStateException("Payroll run " + run.getRunNumber() + " cannot be paid in status " + run.getStatus());
        }
        UserAccount payer = resolveUser(userId);

        JournalEntry paymentEntry = postPaymentToGeneralLedger(run);
        run.setPaymentJournalEntry(paymentEntry);
        run.setStatus(PayrollRunStatus.PAID);
        run.setPaidAt(java.time.LocalDateTime.now());
        PayrollRun saved = payrollRunRepository.save(run);

        log.info("Payroll run {} paid by {} - GL entry #{}", saved.getRunNumber(), payer.getUsername(), paymentEntry.getEntryNumber());
        return toResponse(saved);
    }

    private JournalEntry postAccrualToGeneralLedger(PayrollRun run) {
        Account salaryExpense = accountRepository.findByCode(SALARY_EXPENSE_ACCOUNT_CODE)
                .orElseThrow(() -> new IllegalStateException("Chart of accounts is missing " + SALARY_EXPENSE_ACCOUNT_CODE));
        Account payrollPayable = accountRepository.findByCode(PAYROLL_PAYABLE_ACCOUNT_CODE)
                .orElseThrow(() -> new IllegalStateException("Chart of accounts is missing " + PAYROLL_PAYABLE_ACCOUNT_CODE));
        Account deductionsPayable = accountRepository.findByCode(PAYROLL_DEDUCTIONS_PAYABLE_ACCOUNT_CODE)
                .orElseThrow(() -> new IllegalStateException("Chart of accounts is missing " + PAYROLL_DEDUCTIONS_PAYABLE_ACCOUNT_CODE));

        String memo = "Payroll accrual " + run.getRunNumber() + " (" + run.getPeriodStart() + " to " + run.getPeriodEnd() + ")";
        Currency currency = run.getCurrency();
        BigDecimal exchangeRate = exchangeRateToBase(currency);
        List<ManualLineSpec> specs = new ArrayList<>();
        specs.add(new ManualLineSpec(salaryExpense, run.getTotalGrossPay(), BigDecimal.ZERO, currency, exchangeRate, null, memo));
        specs.add(new ManualLineSpec(payrollPayable, BigDecimal.ZERO, run.getTotalNetPay(), currency, exchangeRate, null, memo));
        if (run.getTotalDeductions().compareTo(BigDecimal.ZERO) > 0) {
            specs.add(new ManualLineSpec(deductionsPayable, BigDecimal.ZERO, run.getTotalDeductions(), currency, exchangeRate, null, memo));
        }

        return glPostingService.postManual(
                "PAYROLL-ACCRUAL-" + run.getRunNumber(), run.getPayDate(), memo,
                GLSourceModule.SYSTEM, "PAYROLL_RUN", run.getId(), specs, run.getCreatedBy().getUsername());
    }

    private JournalEntry postPaymentToGeneralLedger(PayrollRun run) {
        Account payrollPayable = accountRepository.findByCode(PAYROLL_PAYABLE_ACCOUNT_CODE)
                .orElseThrow(() -> new IllegalStateException("Chart of accounts is missing " + PAYROLL_PAYABLE_ACCOUNT_CODE));
        Account cashOrBank = accountRepository.findByCode(run.getPaymentMethod() == PaymentMethod.CASH ? "1010" : "1030")
                .orElseThrow(() -> new IllegalStateException("Chart of accounts is missing the cash/bank account"));

        String memo = "Payroll payment " + run.getRunNumber();
        Currency currency = run.getCurrency();
        BigDecimal exchangeRate = exchangeRateToBase(currency);
        List<ManualLineSpec> specs = List.of(
                new ManualLineSpec(payrollPayable, run.getTotalNetPay(), BigDecimal.ZERO, currency, exchangeRate, null, memo),
                new ManualLineSpec(cashOrBank, BigDecimal.ZERO, run.getTotalNetPay(), currency, exchangeRate, null, memo));

        return glPostingService.postManual(
                "PAYROLL-PAYMENT-" + run.getRunNumber(), java.time.LocalDate.now(), memo,
                GLSourceModule.SYSTEM, "PAYROLL_RUN", run.getId(), specs, run.getCreatedBy().getUsername());
    }

    private BigDecimal exchangeRateToBase(Currency currency) {
        Currency baseCurrency = currencyService.getBaseCurrency();
        return currency == null || currency.equals(baseCurrency)
                ? BigDecimal.ONE : currencyService.getExchangeRate(currency, baseCurrency);
    }

    private UserAccount resolveUser(Long userId) {
        return userAccountRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
    }

    private PayrollRun findOrThrow(Long id) {
        return payrollRunRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Payroll run not found: " + id));
    }

    private PayrollRunResponse toResponse(PayrollRun run) {
        return toResponse(run, payslipRepository.findByPayrollRun(run));
    }

    private PayrollRunResponse toResponse(PayrollRun run, List<Payslip> payslips) {
        return PayrollRunResponse.builder()
                .id(run.getId())
                .runNumber(run.getRunNumber())
                .periodStart(run.getPeriodStart())
                .periodEnd(run.getPeriodEnd())
                .payDate(run.getPayDate())
                .currencyCode(run.getCurrency() != null ? run.getCurrency().getCode() : null)
                .totalGrossPay(run.getTotalGrossPay())
                .totalDeductions(run.getTotalDeductions())
                .totalNetPay(run.getTotalNetPay())
                .paymentMethod(run.getPaymentMethod() != null ? run.getPaymentMethod().name() : null)
                .status(run.getStatus().name())
                .createdById(run.getCreatedBy() != null ? run.getCreatedBy().getId() : null)
                .createdByUsername(run.getCreatedBy() != null ? run.getCreatedBy().getUsername() : null)
                .createdAt(run.getCreatedAt())
                .accrualJournalEntryId(run.getAccrualJournalEntry() != null ? run.getAccrualJournalEntry().getId() : null)
                .accrualJournalEntryNumber(run.getAccrualJournalEntry() != null ? run.getAccrualJournalEntry().getEntryNumber() : null)
                .paymentJournalEntryId(run.getPaymentJournalEntry() != null ? run.getPaymentJournalEntry().getId() : null)
                .paymentJournalEntryNumber(run.getPaymentJournalEntry() != null ? run.getPaymentJournalEntry().getEntryNumber() : null)
                .paidAt(run.getPaidAt())
                .payslips(payslips.stream().map(this::toPayslipResponse).collect(Collectors.toList()))
                .build();
    }

    private PayslipResponse toPayslipResponse(Payslip payslip) {
        return PayslipResponse.builder()
                .id(payslip.getId())
                .employeeId(payslip.getEmployee().getId())
                .employeeNumber(payslip.getEmployee().getEmployeeNumber())
                .employeeName(payslip.getEmployee().getFullName())
                .currencyCode(payslip.getCurrency() != null ? payslip.getCurrency().getCode() : null)
                .grossPay(payslip.getGrossPay())
                .totalDeductions(payslip.getTotalDeductions())
                .netPay(payslip.getNetPay())
                .build();
    }
}
