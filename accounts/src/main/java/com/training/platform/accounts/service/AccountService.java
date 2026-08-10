package com.training.platform.accounts.service;

import com.training.platform.accounts.entity.Account;
import com.training.platform.accounts.repository.AccountRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AccountService {
    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) { this.accountRepository = accountRepository; }

    public Account getById(String accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new EntityNotFoundException("Account not found: " + accountId));
    }

    public Account getByAccountNumber(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new EntityNotFoundException("Account not found: " + accountNumber));
    }

    public List<Account> getByCustomerId(String customerId) { return accountRepository.findByCustomerId(customerId); }

    @Transactional
    public Account create(Account account) {
        validate(account);
        return accountRepository.save(account);
    }

    @Transactional
    public Account update(String accountId, Account account) {
        Account existing = getById(accountId);
        existing.setAccountNumber(account.getAccountNumber());
        existing.setCustomerId(account.getCustomerId());
        existing.setProductId(account.getProductId());
        existing.setOwnershipType(account.getOwnershipType());
        existing.setStatus(account.getStatus());
        existing.setCurrencyCode(account.getCurrencyCode());
        existing.setAvailableBalance(account.getAvailableBalance());
        existing.setClosedAt(account.getClosedAt());
        validate(existing);
        return accountRepository.save(existing);
    }

    private void validate(Account account) {
        if (account == null) {
            throw new IllegalArgumentException("Account is required");
        }
        if (account.getOwnershipType() == null) {
            throw new IllegalArgumentException("Ownership type is required");
        }
        if (account.getStatus() == null) {
            throw new IllegalArgumentException("Account status is required");
        }
        if (isBlank(account.getAccountNumber())) throw new IllegalArgumentException("Account number is required");
        if (isBlank(account.getCustomerId())) throw new IllegalArgumentException("Customer ID is required");
        if (isBlank(account.getProductId())) throw new IllegalArgumentException("Product ID is required");
        if (isBlank(account.getCurrencyCode())) throw new IllegalArgumentException("Currency code is required");
        if (account.getAvailableBalance() == null || account.getAvailableBalance().signum() < 0) {
            throw new IllegalArgumentException("Available balance cannot be negative");
        }
    }

    private boolean isBlank(String value) { return value == null || value.isBlank(); }
}
