package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.account.Account;
import com.pos_onlineshop.hybrid.account.AccountRepository;
import com.pos_onlineshop.hybrid.dtos.PostingRuleLineRequest;
import com.pos_onlineshop.hybrid.dtos.PostingRuleLineResponse;
import com.pos_onlineshop.hybrid.dtos.PostingRuleRequest;
import com.pos_onlineshop.hybrid.dtos.PostingRuleResponse;
import com.pos_onlineshop.hybrid.postingRule.PostingRule;
import com.pos_onlineshop.hybrid.postingRule.PostingRuleLine;
import com.pos_onlineshop.hybrid.postingRule.PostingRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Administration of PostingRule/PostingRuleLine - the configurable event-type-to-accounts
 * mapping GLPostingService reads at post() time. Changing a rule only affects events
 * posted after the change; already-posted journal lines are never touched.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PostingRuleService {

    private final PostingRuleRepository postingRuleRepository;
    private final AccountRepository accountRepository;

    public List<PostingRuleResponse> findAll() {
        return postingRuleRepository.findAll().stream().map(this::toResponse).collect(Collectors.toList());
    }

    public PostingRuleResponse findById(Long id) {
        return toResponse(findOrThrow(id));
    }

    public PostingRuleResponse create(PostingRuleRequest request) {
        if (postingRuleRepository.existsByEventType(request.getEventType())) {
            throw new IllegalArgumentException(
                    "A posting rule already exists for event type " + request.getEventType());
        }
        PostingRule rule = PostingRule.builder()
                .eventType(request.getEventType())
                .description(request.getDescription())
                .active(request.isActive())
                .build();
        applyLines(rule, request.getLines());
        return toResponse(postingRuleRepository.save(rule));
    }

    /** Replaces the rule's description and its entire line set; eventType is immutable. */
    public PostingRuleResponse update(Long id, PostingRuleRequest request) {
        PostingRule rule = findOrThrow(id);
        if (rule.getEventType() != request.getEventType()) {
            throw new IllegalArgumentException("A posting rule's event type cannot be changed after creation");
        }
        rule.setDescription(request.getDescription());
        rule.getLines().clear();
        applyLines(rule, request.getLines());
        return toResponse(postingRuleRepository.save(rule));
    }

    public PostingRuleResponse activate(Long id) {
        PostingRule rule = findOrThrow(id);
        rule.setActive(true);
        return toResponse(postingRuleRepository.save(rule));
    }

    public PostingRuleResponse deactivate(Long id) {
        PostingRule rule = findOrThrow(id);
        rule.setActive(false);
        return toResponse(postingRuleRepository.save(rule));
    }

    private void applyLines(PostingRule rule, List<PostingRuleLineRequest> lineRequests) {
        for (PostingRuleLineRequest lineRequest : lineRequests) {
            Account account = accountRepository.findById(lineRequest.getAccountId())
                    .orElseThrow(() -> new IllegalArgumentException("Account not found: " + lineRequest.getAccountId()));
            PostingRuleLine line = PostingRuleLine.builder()
                    .account(account)
                    .side(lineRequest.getSide())
                    .amountSource(lineRequest.getAmountSource())
                    .sequence(lineRequest.getSequence())
                    .shopRole(lineRequest.getShopRole())
                    .build();
            rule.addLine(line);
        }
    }

    private PostingRule findOrThrow(Long id) {
        return postingRuleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Posting rule not found: " + id));
    }

    private PostingRuleResponse toResponse(PostingRule rule) {
        List<PostingRuleLineResponse> lines = rule.getLines().stream()
                .map(line -> PostingRuleLineResponse.builder()
                        .id(line.getId())
                        .accountId(line.getAccount().getId())
                        .accountCode(line.getAccount().getCode())
                        .accountName(line.getAccount().getName())
                        .side(line.getSide())
                        .amountSource(line.getAmountSource())
                        .sequence(line.getSequence())
                        .shopRole(line.getShopRole())
                        .build())
                .collect(Collectors.toList());
        return PostingRuleResponse.builder()
                .id(rule.getId())
                .eventType(rule.getEventType())
                .description(rule.getDescription())
                .active(rule.isActive())
                .lines(lines)
                .build();
    }
}
