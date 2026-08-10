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
    public BankTransaction initiate(BankTransaction transaction) {
        validate(transaction);
        return transactionRepository.save(transaction);
    }

    @Transactional
    public BankTransaction update(String transactionId, BankTransaction transaction) {
        BankTransaction existing = getById(transactionId);
        copy(transaction, existing);
        validate(existing);
        return transactionRepository.save(existing);
    }

    private void validate(BankTransaction transaction) {
        if (transaction == null) {
            throw new IllegalArgumentException("Transaction is required");
        }
        if (transaction.getTransactionType() == null) {
            throw new IllegalArgumentException("Transaction type is required");
        }
        if (transaction.getTransactionStatus() == null) {
            throw new IllegalArgumentException("Transaction status is required");
        }
        if (transaction.getTransactionChannel() == null) {
            throw new IllegalArgumentException("Transaction channel is required");
        }
        if (isBlank(transaction.getTransactionRef())) throw new IllegalArgumentException("Transaction reference is required");
        if (transaction.getAmount() == null || transaction.getAmount().signum() <= 0) throw new IllegalArgumentException("Amount must be greater than zero");
        if (isBlank(transaction.getCurrencyCode())) throw new IllegalArgumentException("Currency code is required");
    }

    private void copy(BankTransaction source, BankTransaction target) {
        target.setTransactionRef(source.getTransactionRef());
        target.setTransactionType(source.getTransactionType());
        target.setTransactionStatus(source.getTransactionStatus());
        target.setTransactionChannel(source.getTransactionChannel());
        target.setDebitAccountId(source.getDebitAccountId());
        target.setCreditAccountId(source.getCreditAccountId());
        target.setExternalBeneficiary(source.getExternalBeneficiary());
        target.setAmount(source.getAmount());
        target.setCurrencyCode(source.getCurrencyCode());
        target.setFeeAmount(source.getFeeAmount());
        target.setInitiatedByCustomerId(source.getInitiatedByCustomerId());
        target.setInitiatedByUserId(source.getInitiatedByUserId());
        target.setCompletedAt(source.getCompletedAt());
        target.setFailureCode(source.getFailureCode());
        target.setFailureReason(source.getFailureReason());
    }

    private boolean isBlank(String value) { return value == null || value.isBlank(); }
}
