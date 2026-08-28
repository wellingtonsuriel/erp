package com.pos_onlineshop.hybrid.postingRule;

import com.pos_onlineshop.hybrid.account.Account;
import com.pos_onlineshop.hybrid.enums.AmountSource;
import com.pos_onlineshop.hybrid.enums.DebitCredit;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "gl_posting_rule_lines")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"postingRule", "account"})
@ToString(exclude = {"postingRule", "account"})
public class PostingRuleLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "posting_rule_id", nullable = false)
    private PostingRule postingRule;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DebitCredit side;

    /** Which slice of the FinancialEvent's amounts this line pulls its value from. */
    @Enumerated(EnumType.STRING)
    @Column(name = "amount_source", nullable = false)
    private AmountSource amountSource;

    @Column(nullable = false)
    private int sequence;
}
