package com.pos_onlineshop.hybrid.expense;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {
    boolean existsByExpenseNumber(String expenseNumber);

    List<Expense> findAllByOrderByIdDesc();
}
