package com.training.platform.transactions.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.training.platform.transactions.client.AccountPostingException;
import com.training.platform.transactions.client.AccountAdjustmentRequest;
import com.training.platform.transactions.client.AccountAdjustmentResponse;
import com.training.platform.transactions.client.AccountTransferRequest;
import com.training.platform.transactions.client.AccountTransferResponse;
import com.training.platform.transactions.client.AccountsClient;
import com.training.platform.transactions.entity.AccountStatement;
import com.training.platform.transactions.entity.BankTransaction;
import com.training.platform.transactions.entity.StatementEntryType;
import com.training.platform.transactions.entity.TransactionEventOutbox;
import com.training.platform.transactions.entity.TransactionEventType;
import com.training.platform.transactions.entity.TransactionStatus;
import com.training.platform.transactions.entity.TransactionType;
import com.training.platform.transactions.repository.AccountStatementRepository;
import com.training.platform.transactions.repository.BankTransactionRepository;
import com.training.platform.transactions.repository.TransactionEventOutboxRepository;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BankTransactionService {
    private final BankTransactionRepository transactionRepository;
    private final AccountStatementRepository statementRepository;
    private final TransactionEventOutboxRepository outboxRepository;
    private final AccountsClient accountsClient;
    private final ObjectMapper objectMapper;

    public BankTransactionService(BankTransactionRepository transactionRepository,
                                  AccountStatementRepository statementRepository,
                                  TransactionEventOutboxRepository outboxRepository,
                                  AccountsClient accountsClient,
                                  ObjectMapper objectMapper) {
        this.transactionRepository = transactionRepository;
        this.statementRepository = statementRepository;
        this.outboxRepository = outboxRepository;
        this.accountsClient = accountsClient;
        this.objectMapper = objectMapper;
    }

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
        validateForInitiation(transaction);
        BankTransaction existing = transactionRepository.findByTransactionRef(transaction.getTransactionRef())
                .orElse(null);
        if (existing != null) {
            if (!samePosting(existing, transaction)) {
                throw new IllegalArgumentException(
                        "Transaction reference was already used for a different transfer");
            }
            return existing;
        }

        transaction.setTransactionStatus(TransactionStatus.PROCESSING);
        transaction.setCompletedAt(null);
        transaction.setFailureCode(null);
        transaction.setFailureReason(null);
        BankTransaction persisted = transactionRepository.saveAndFlush(transaction);

        try {
            if (persisted.getTransactionType() == TransactionType.TRANSFER) {
                AccountTransferResponse transfer = accountsClient.transfer(new AccountTransferRequest(
                        persisted.getTransactionRef(), persisted.getDebitAccountId(), persisted.getCreditAccountId(),
                        persisted.getAmount(), persisted.getCurrencyCode(), persisted.getInitiatedByCustomerId()));
                validateTransferResponse(persisted, transfer);
                completeTransfer(persisted, transfer);
            } else {
                String accountId = postingAccountId(persisted);
                AccountAdjustmentRequest.AdjustmentType adjustmentType =
                        AccountAdjustmentRequest.AdjustmentType.valueOf(persisted.getTransactionType().name());
                AccountAdjustmentResponse adjustment = accountsClient.adjust(accountId,
                        new AccountAdjustmentRequest(persisted.getTransactionRef(), adjustmentType,
                                persisted.getAmount(), persisted.getCurrencyCode()));
                validateAdjustmentResponse(persisted, accountId, adjustment);
                completeAdjustment(persisted, accountId, adjustment);
            }
        } catch (AccountPostingException exception) {
            fail(persisted, exception);
        }
        return transactionRepository.save(persisted);
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
        if (isBlank(transaction.getTransactionRef())) throw new IllegalArgumentException("Transaction reference is required");
        if (transaction.getAmount() == null || transaction.getAmount().signum() <= 0) throw new IllegalArgumentException("Amount must be greater than zero");
        if (isBlank(transaction.getCurrencyCode())) throw new IllegalArgumentException("Currency code is required");
    }

    private void validateForInitiation(BankTransaction transaction) {
        if (transaction == null) throw new IllegalArgumentException("Transaction is required");
        if (transaction.getTransactionType() == null) {
            throw new IllegalArgumentException("Transaction type is required");
        }
        if (transaction.getTransactionType() != TransactionType.TRANSFER
                && transaction.getTransactionType() != TransactionType.DEPOSIT
                && transaction.getTransactionType() != TransactionType.WITHDRAWAL) {
            throw new IllegalArgumentException("Only TRANSFER, DEPOSIT, and WITHDRAWAL posting is supported");
        }
        if (isBlank(transaction.getTransactionRef())) {
            throw new IllegalArgumentException("Transaction reference is required");
        }
        if (transaction.getTransactionType() == TransactionType.TRANSFER) {
            if (isBlank(transaction.getDebitAccountId()) || isBlank(transaction.getCreditAccountId())) {
                throw new IllegalArgumentException("Debit and credit account IDs are required");
            }
            if (transaction.getDebitAccountId().equals(transaction.getCreditAccountId())) {
                throw new IllegalArgumentException("Debit and credit accounts must be different");
            }
        } else if (transaction.getTransactionType() == TransactionType.DEPOSIT
                && isBlank(transaction.getCreditAccountId())) {
            throw new IllegalArgumentException("Credit account ID is required for a deposit");
        } else if (transaction.getTransactionType() == TransactionType.WITHDRAWAL
                && isBlank(transaction.getDebitAccountId())) {
            throw new IllegalArgumentException("Debit account ID is required for a withdrawal");
        }
        if (transaction.getAmount() == null || transaction.getAmount().signum() <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        if (isBlank(transaction.getCurrencyCode())) {
            throw new IllegalArgumentException("Currency code is required");
        }
        BigDecimal feeAmount = transaction.getFeeAmount() == null ? BigDecimal.ZERO : transaction.getFeeAmount();
        if (feeAmount.signum() != 0) {
            throw new IllegalArgumentException("Fees require a configured bank fee account and are not posted yet");
        }
    }

    private void validateTransferResponse(BankTransaction transaction, AccountTransferResponse response) {
        if (!transaction.getTransactionRef().equals(response.transactionRef())
                || !transaction.getDebitAccountId().equals(response.debitAccountId())
                || !transaction.getCreditAccountId().equals(response.creditAccountId())) {
            throw new AccountPostingException("ACCOUNT_RESPONSE_INVALID",
                    "Accounts service returned a response for a different transfer");
        }
    }

    private void validateAdjustmentResponse(BankTransaction transaction, String accountId,
                                            AccountAdjustmentResponse response) {
        if (!transaction.getTransactionRef().equals(response.transactionRef())
                || !accountId.equals(response.accountId())
                || transaction.getTransactionType() != TransactionType.valueOf(response.adjustmentType().name())) {
            throw new AccountPostingException("ACCOUNT_RESPONSE_INVALID",
                    "Accounts service returned a response for a different posting");
        }
    }

    private boolean samePosting(BankTransaction existing, BankTransaction requested) {
        return existing.getTransactionType() == requested.getTransactionType()
                && Objects.equals(existing.getDebitAccountId(), requested.getDebitAccountId())
                && Objects.equals(existing.getCreditAccountId(), requested.getCreditAccountId())
                && existing.getAmount().compareTo(requested.getAmount()) == 0
                && existing.getCurrencyCode().equalsIgnoreCase(requested.getCurrencyCode());
    }

    private void completeTransfer(BankTransaction transaction, AccountTransferResponse transfer) {
        transaction.setTransactionStatus(TransactionStatus.COMPLETED);
        transaction.setCompletedAt(LocalDateTime.now());

        AccountStatement debitEntry = statement(transaction, transaction.getDebitAccountId(),
                StatementEntryType.DEBIT, transfer.debitBalanceAfter());
        AccountStatement creditEntry = statement(transaction, transaction.getCreditAccountId(),
                StatementEntryType.CREDIT, transfer.creditBalanceAfter());
        statementRepository.saveAll(List.of(debitEntry, creditEntry));

        Map<String, Object> payload = basePayload(transaction);
        payload.put("debitBalanceAfter", transfer.debitBalanceAfter());
        payload.put("creditBalanceAfter", transfer.creditBalanceAfter());
        outboxRepository.save(TransactionEventOutbox.create(transaction.getTransactionId(),
                TransactionEventType.TRANSACTION_COMPLETED, toJson(payload)));
    }

    private void completeAdjustment(BankTransaction transaction, String accountId,
                                    AccountAdjustmentResponse adjustment) {
        transaction.setTransactionStatus(TransactionStatus.COMPLETED);
        transaction.setCompletedAt(LocalDateTime.now());

        StatementEntryType entryType = transaction.getTransactionType() == TransactionType.DEPOSIT
                ? StatementEntryType.CREDIT : StatementEntryType.DEBIT;
        statementRepository.save(statement(transaction, accountId, entryType, adjustment.balanceAfter()));

        Map<String, Object> payload = basePayload(transaction);
        payload.put("accountId", accountId);
        payload.put("balanceAfter", adjustment.balanceAfter());
        outboxRepository.save(TransactionEventOutbox.create(transaction.getTransactionId(),
                TransactionEventType.TRANSACTION_COMPLETED, toJson(payload)));
    }

    private String postingAccountId(BankTransaction transaction) {
        return transaction.getTransactionType() == TransactionType.DEPOSIT
                ? transaction.getCreditAccountId() : transaction.getDebitAccountId();
    }

    private void fail(BankTransaction transaction, AccountPostingException exception) {
        transaction.setTransactionStatus(TransactionStatus.FAILED);
        transaction.setFailureCode(limit(exception.getFailureCode(), 50));
        transaction.setFailureReason(limit(exception.getMessage(), 500));
        Map<String, Object> payload = basePayload(transaction);
        payload.put("failureCode", transaction.getFailureCode());
        payload.put("failureReason", transaction.getFailureReason());
        outboxRepository.save(TransactionEventOutbox.create(transaction.getTransactionId(),
                TransactionEventType.TRANSACTION_FAILED, toJson(payload)));
    }

    private AccountStatement statement(BankTransaction transaction, String accountId,
                                       StatementEntryType entryType, BigDecimal balanceAfter) {
        AccountStatement statement = new AccountStatement();
        statement.setTransaction(transaction);
        statement.setAccountId(accountId);
        statement.setEntryType(entryType);
        statement.setAmount(transaction.getAmount());
        statement.setCurrencyCode(transaction.getCurrencyCode());
        statement.setBalanceAfter(balanceAfter);
        return statement;
    }

    private Map<String, Object> basePayload(BankTransaction transaction) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("transactionId", transaction.getTransactionId());
        payload.put("transactionRef", transaction.getTransactionRef());
        payload.put("transactionType", transaction.getTransactionType());
        payload.put("transactionStatus", transaction.getTransactionStatus());
        payload.put("debitAccountId", transaction.getDebitAccountId());
        payload.put("creditAccountId", transaction.getCreditAccountId());
        payload.put("amount", transaction.getAmount());
        payload.put("currencyCode", transaction.getCurrencyCode());
        return payload;
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize transaction event", exception);
        }
    }

    private String limit(String value, int maxLength) {
        if (value == null) return null;
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private void copy(BankTransaction source, BankTransaction target) {
        target.setTransactionRef(source.getTransactionRef());
        target.setTransactionType(source.getTransactionType());
        target.setTransactionStatus(source.getTransactionStatus());
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
