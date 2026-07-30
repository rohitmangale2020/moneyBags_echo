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
    public Account create(Account account) { return accountRepository.save(account); }

    @Transactional
    public Account update(Account account) { return accountRepository.save(account); }
}
