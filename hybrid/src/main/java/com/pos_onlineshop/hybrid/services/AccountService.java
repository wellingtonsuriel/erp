package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.account.Account;
import com.pos_onlineshop.hybrid.account.AccountRepository;
import com.pos_onlineshop.hybrid.dtos.AccountResponse;
import com.pos_onlineshop.hybrid.dtos.CreateAccountRequest;
import com.pos_onlineshop.hybrid.dtos.UpdateAccountRequest;
import com.pos_onlineshop.hybrid.journalLine.JournalLineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Chart-of-accounts administration. Deliberately has no delete operation - an account
 * that has ever appeared on a JournalLine must remain resolvable forever so historical
 * journals stay readable; deactivate() is the correct way to retire an account.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AccountService {

    private final AccountRepository accountRepository;
    private final JournalLineRepository journalLineRepository;

    public List<AccountResponse> findAll(Boolean activeOnly) {
        List<Account> accounts = Boolean.TRUE.equals(activeOnly)
                ? accountRepository.findByActiveTrue()
                : accountRepository.findAll();
        return accounts.stream().map(this::toResponse).collect(Collectors.toList());
    }

    public AccountResponse findById(Long id) {
        return toResponse(findOrThrow(id));
    }

    public AccountResponse create(CreateAccountRequest request) {
        if (accountRepository.existsByCode(request.getCode())) {
            throw new IllegalArgumentException("Account code already exists: " + request.getCode());
        }
        Account parent = resolveParent(request.getParentAccountId());

        Account account = Account.builder()
                .code(request.getCode())
                .name(request.getName())
                .accountType(request.getAccountType())
                .normalBalance(request.getNormalBalance())
                .parentAccount(parent)
                .controlAccount(request.isControlAccount())
                .active(request.isActive())
                .build();
        return toResponse(accountRepository.save(account));
    }

    public AccountResponse update(Long id, UpdateAccountRequest request) {
        Account account = findOrThrow(id);

        if (account.getAccountType() != request.getAccountType()
                && journalLineRepository.existsByAccount(account)) {
            throw new IllegalStateException(
                    "Account " + account.getCode() + " has journal history - its account type is immutable");
        }

        Account parent = resolveParent(request.getParentAccountId());
        if (parent != null && parent.getId().equals(id)) {
            throw new IllegalArgumentException("An account cannot be its own parent");
        }

        account.setName(request.getName());
        account.setAccountType(request.getAccountType());
        account.setNormalBalance(request.getNormalBalance());
        account.setParentAccount(parent);
        account.setControlAccount(request.isControlAccount());
        return toResponse(accountRepository.save(account));
    }

    public AccountResponse activate(Long id) {
        Account account = findOrThrow(id);
        account.setActive(true);
        return toResponse(accountRepository.save(account));
    }

    public AccountResponse deactivate(Long id) {
        Account account = findOrThrow(id);
        account.setActive(false);
        return toResponse(accountRepository.save(account));
    }

    private Account resolveParent(Long parentAccountId) {
        if (parentAccountId == null) {
            return null;
        }
        return accountRepository.findById(parentAccountId)
                .orElseThrow(() -> new IllegalArgumentException("Parent account not found: " + parentAccountId));
    }

    private Account findOrThrow(Long id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + id));
    }

    private AccountResponse toResponse(Account account) {
        return AccountResponse.builder()
                .id(account.getId())
                .code(account.getCode())
                .name(account.getName())
                .accountType(account.getAccountType())
                .normalBalance(account.getNormalBalance())
                .parentAccountId(account.getParentAccount() != null ? account.getParentAccount().getId() : null)
                .parentAccountCode(account.getParentAccount() != null ? account.getParentAccount().getCode() : null)
                .controlAccount(account.isControlAccount())
                .active(account.isActive())
                .hasJournalHistory(journalLineRepository.existsByAccount(account))
                .build();
    }
}
