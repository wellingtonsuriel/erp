package com.pos_onlineshop.hybrid.expenseCategory;

import jakarta.persistence.*;
import lombok.*;

/** Groups expenses for reporting and picks which GL expense account they post to - most
 * categories map to the general 5300 Operating Expenses account, but a category can point
 * at a more specific one (e.g. a dedicated account) without any code change. */
@Entity
@Table(name = "expense_categories")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExpenseCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "gl_account_code", nullable = false, length = 20)
    @Builder.Default
    private String glAccountCode = "5300";

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}
