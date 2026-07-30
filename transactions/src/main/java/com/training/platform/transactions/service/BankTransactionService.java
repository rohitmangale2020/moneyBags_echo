package com.training.platform.transactions.service;

import com.training.platform.transactions.entity.BankTransaction;
import com.training.platform.transactions.repository.BankTransactionRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BankTransactionService {
    private final BankTransactionRepository transactionRepository;

    public BankTransactionService(BankTransactionRepository transactionRepository) { this.transactionRepository = transactionRepository; }

    public BankTransaction getById(String transactionId) {
        return transactionRepository.findById(transactionId)
                .orElseThrow(() -> new EntityNotFoundException("Transaction not found: " + transactionId));
    }

    public BankTransaction getByReference(String transactionRef) {
        return transactionRepository.findByTransactionRef(transactionRef)
                .orElseThrow(() -> new EntityNotFoundException("Transaction not found: " + transactionRef));
    }

    public List<BankTransaction> getDebitAccountTransactions(String accountId) { return transactionRepository.findByDebitAccountId(accountId); }
    public List<BankTransaction> getCreditAccountTransactions(String accountId) { return transactionRepository.findByCreditAccountId(accountId); }

    @Transactional
    public BankTransaction initiate(BankTransaction transaction) { return transactionRepository.save(transaction); }

    @Transactional
    public BankTransaction update(BankTransaction transaction) { return transactionRepository.save(transaction); }
}
