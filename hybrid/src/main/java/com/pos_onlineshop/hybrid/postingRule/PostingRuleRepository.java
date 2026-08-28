package com.pos_onlineshop.hybrid.postingRule;

import com.pos_onlineshop.hybrid.enums.FinancialEventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PostingRuleRepository extends JpaRepository<PostingRule, Long> {

    Optional<PostingRule> findByEventTypeAndActiveTrue(FinancialEventType eventType);

    boolean existsByEventType(FinancialEventType eventType);
}
