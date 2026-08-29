package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.account.AccountRepository;
import com.pos_onlineshop.hybrid.dtos.CreateExpenseCategoryRequest;
import com.pos_onlineshop.hybrid.expenseCategory.ExpenseCategory;
import com.pos_onlineshop.hybrid.expenseCategory.ExpenseCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ExpenseCategoryService {

    private final ExpenseCategoryRepository expenseCategoryRepository;
    private final AccountRepository accountRepository;

    @Transactional(readOnly = true)
    public List<ExpenseCategory> findAllActive() {
        return expenseCategoryRepository.findByActiveTrue();
    }

    public ExpenseCategory create(CreateExpenseCategoryRequest request) {
        if (expenseCategoryRepository.existsByName(request.getName())) {
            throw new IllegalArgumentException("An expense category named '" + request.getName() + "' already exists");
        }
        String glAccountCode = request.getGlAccountCode() != null ? request.getGlAccountCode() : "5300";
        if (accountRepository.findByCode(glAccountCode).isEmpty()) {
            throw new IllegalArgumentException("Account " + glAccountCode + " does not exist in the chart of accounts");
        }
        return expenseCategoryRepository.save(ExpenseCategory.builder()
                .name(request.getName())
                .glAccountCode(glAccountCode)
                .build());
    }
}
