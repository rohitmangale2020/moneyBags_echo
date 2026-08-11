package com.training.platform.accounts.service;

import com.training.platform.accounts.entity.Account;
import com.training.platform.accounts.entity.AccountHolder;
import com.training.platform.accounts.entity.AccountStatusHistory;
import com.training.platform.accounts.repository.AccountHolderRepository;
import com.training.platform.accounts.repository.AccountRepository;
import com.training.platform.accounts.repository.AccountStatusHistoryRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AccountService {
    private final AccountRepository accountRepository;
    private final AccountHolderRepository accountHolderRepository;
    private final AccountStatusHistoryRepository accountStatusHistoryRepository;

    public AccountService(AccountRepository accountRepository, AccountHolderRepository accountHolderRepository,
                          AccountStatusHistoryRepository accountStatusHistoryRepository) {
        this.accountRepository = accountRepository;
        this.accountHolderRepository = accountHolderRepository;
        this.accountStatusHistoryRepository = accountStatusHistoryRepository;
    }

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
        Account savedAccount = accountRepository.save(account);
        accountHolderRepository.save(AccountHolder.primaryHolder(savedAccount));
        accountStatusHistoryRepository.save(AccountStatusHistory.initialStatus(savedAccount));
        return savedAccount;
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
