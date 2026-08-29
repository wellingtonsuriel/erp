package com.pos_onlineshop.hybrid.expenseCategory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExpenseCategoryRepository extends JpaRepository<ExpenseCategory, Long> {
    boolean existsByName(String name);

    List<ExpenseCategory> findByActiveTrue();
}
