package com.pos_onlineshop.hybrid.postingRule;

import com.pos_onlineshop.hybrid.enums.FinancialEventType;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Configurable mapping of a business event type to the accounts it should hit.
 * Adding a new event type is a data change (a PostingRule + its lines), not a code
 * change inside a business service.
 */
@Entity
@Table(name = "gl_posting_rules")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"lines"})
@ToString(exclude = {"lines"})
public class PostingRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, unique = true)
    private FinancialEventType eventType;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(length = 300)
    private String description;

    @OneToMany(mappedBy = "postingRule", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<PostingRuleLine> lines = new ArrayList<>();

    public void addLine(PostingRuleLine line) {
        lines.add(line);
        line.setPostingRule(this);
    }
}
