package com.training.platform.transactions.service;

import com.training.platform.transactions.entity.AccountStatement;
import com.training.platform.transactions.repository.BankTransactionRepository;
import com.training.platform.transactions.repository.AccountStatementRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AccountStatementService {
    private final AccountStatementRepository statementRepository;
    private final BankTransactionRepository transactionRepository;

    public AccountStatementService(AccountStatementRepository statementRepository, BankTransactionRepository transactionRepository) {
        this.statementRepository = statementRepository;
        this.transactionRepository = transactionRepository;
    }

    public AccountStatement getById(String statementId) {
        return statementRepository.findById(statementId)
                .orElseThrow(() -> new EntityNotFoundException("Statement not found: " + statementId));
    }

    public List<AccountStatement> getByAccountId(String accountId) {
        return statementRepository.findByAccountIdOrderByPostedAtDesc(accountId);
    }

    @Transactional
    public AccountStatement record(String transactionId, AccountStatement statement) {
        statement.setTransaction(transactionRepository.findById(transactionId)
                .orElseThrow(() -> new EntityNotFoundException("Transaction not found: " + transactionId)));
        validate(statement);
        return statementRepository.save(statement);
    }

    private void validate(AccountStatement statement) {
        if (statement == null) throw new IllegalArgumentException("Statement is required");
        if (statement.getTransaction() == null) throw new IllegalArgumentException("Transaction is required");
        if (isBlank(statement.getAccountId())) throw new IllegalArgumentException("Account ID is required");
        if (statement.getEntryType() == null) throw new IllegalArgumentException("Entry type is required");
        if (statement.getAmount() == null || statement.getAmount().signum() <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        if (isBlank(statement.getCurrencyCode())) throw new IllegalArgumentException("Currency code is required");
        if (statement.getBalanceAfter() == null) throw new IllegalArgumentException("Balance after is required");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
