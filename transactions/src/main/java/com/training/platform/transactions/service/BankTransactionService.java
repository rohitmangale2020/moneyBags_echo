package com.training.platform.transactions.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.training.platform.auditclient.AuditClient;
import com.training.platform.transactions.client.AccountPostingException;
import com.training.platform.transactions.client.AccountAdjustmentRequest;
import com.training.platform.transactions.client.AccountAdjustmentResponse;
import com.training.platform.transactions.client.AccountTransferRequest;
import com.training.platform.transactions.client.AccountTransferResponse;
import com.training.platform.transactions.client.AccountsClient;
import com.training.platform.transactions.client.CustomersClient;
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
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BankTransactionService {
    private final BankTransactionRepository transactionRepository;
    private final AccountStatementRepository statementRepository;
    private final TransactionEventOutboxRepository outboxRepository;
    private final AccountsClient accountsClient;
    private final CustomersClient customersClient;
    private final ObjectMapper objectMapper;
    private final AuditClient auditClient;
    private final LedgerService ledgerService;

    public BankTransactionService(BankTransactionRepository transactionRepository,
                                  AccountStatementRepository statementRepository,
                                  TransactionEventOutboxRepository outboxRepository,
                                  AccountsClient accountsClient,
                                  CustomersClient customersClient,
                                  ObjectMapper objectMapper,
                                  AuditClient auditClient,
                                  LedgerService ledgerService) {
        this.transactionRepository = transactionRepository;
        this.statementRepository = statementRepository;
        this.outboxRepository = outboxRepository;
        this.accountsClient = accountsClient;
        this.customersClient = customersClient;
        this.objectMapper = objectMapper;
        this.auditClient = auditClient;
        this.ledgerService = ledgerService;
    }

    public BankTransaction getById(String transactionId) {
        return transactionRepository.findById(transactionId)
                .orElseThrow(() -> new EntityNotFoundException("Transaction not found: " + transactionId));
    }

    public BankTransaction getByReference(String transactionRef) {
        return transactionRepository.findByTransactionRef(transactionRef)
                .orElseThrow(() -> new EntityNotFoundException("Transaction not found: " + transactionRef));
    }

    public List<BankTransaction> getAllTransactions() {
        return transactionRepository.findAllByOrderByInitiatedAtDesc();
    }

    public Page<BankTransaction> getTransactions(Pageable pageable) {
        return transactionRepository.findAllByOrderByInitiatedAtDesc(pageable);
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
        transaction.setDescription(null);
        transaction.setFailureCode(null);
        transaction.setFailureReason(null);
        BankTransaction persisted = transactionRepository.saveAndFlush(transaction);
        auditTransactionChange("TRANSACTION_INITIATED", persisted, Map.of(),
                "Transaction initiated");

        try {
            if (persisted.getTransactionType() == TransactionType.TRANSFER) {
                AccountTransferResponse transfer = accountsClient.transfer(new AccountTransferRequest(
                        persisted.getTransactionRef(), persisted.getDebitAccountId(), persisted.getCreditAccountId(),
                        persisted.getAmount(), persisted.getCurrencyCode(), persisted.getInitiatedByCustomerId()));
                validateTransferResponse(persisted, transfer);
                completeTransfer(persisted, transfer);
            } else {
                String accountId = postingAccountId(persisted);
                AccountAdjustmentRequest.AdjustmentType adjustmentType = adjustmentType(persisted);
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
        Map<String, Object> previousValues = transactionValues(existing);
        copy(transaction, existing);
        validate(existing);
        BankTransaction saved = transactionRepository.save(existing);
        auditTransactionChange("TRANSACTION_UPDATED", saved, previousValues,
                "Transaction fields changed");
        return saved;
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
                && !isDeposit(transaction)
                && transaction.getTransactionType() != TransactionType.WITHDRAWAL) {
            throw new IllegalArgumentException("Only TRANSFER, DEPOSIT, OPENING_DEPOSIT, FIXED_DEPOSIT_FUNDING, and WITHDRAWAL posting is supported");
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
        } else if (isDeposit(transaction)
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
                || adjustmentType(transaction) != response.adjustmentType()) {
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
        Map<String, Object> previousValues = transactionValues(transaction);
        transaction.setTransactionStatus(TransactionStatus.COMPLETED);
        transaction.setCompletedAt(LocalDateTime.now());

        String debitHolder = customerName(transfer.debitCustomerId());
        String creditHolder = customerName(transfer.creditCustomerId());
        String channel = isSelfTransfer(transaction) ? "SELF TRANSFER" : "INTERNAL TRANSFER";
        transaction.setDescription(limit(channel + " FROM " + debitHolder + " TO " + creditHolder, 500));

        String debitDescription = channel + " TO " + creditHolder + " A/C "
                + maskedAccount(transfer.creditAccountNumber(), transfer.creditAccountId())
                + " | REF " + transaction.getTransactionRef();
        String creditDescription = channel + " FROM " + debitHolder + " A/C "
                + maskedAccount(transfer.debitAccountNumber(), transfer.debitAccountId())
                + " | REF " + transaction.getTransactionRef();

        AccountStatement debitEntry = statement(transaction, transaction.getDebitAccountId(),
                StatementEntryType.DEBIT, transfer.debitBalanceAfter(), debitDescription);
        AccountStatement creditEntry = statement(transaction, transaction.getCreditAccountId(),
                StatementEntryType.CREDIT, transfer.creditBalanceAfter(), creditDescription);
        statementRepository.saveAll(List.of(debitEntry, creditEntry));
        ledgerService.postCompletedTransaction(transaction);
        auditRelatedCreated("STATEMENT_ENTRY_CREATED", transaction, "STATEMENT",
                debitEntry.getStatementId(), "Debit statement entry created", statementValues(debitEntry));
        auditRelatedCreated("STATEMENT_ENTRY_CREATED", transaction, "STATEMENT",
                creditEntry.getStatementId(), "Credit statement entry created", statementValues(creditEntry));

        Map<String, Object> payload = basePayload(transaction);
        payload.put("debitBalanceAfter", transfer.debitBalanceAfter());
        payload.put("creditBalanceAfter", transfer.creditBalanceAfter());
        TransactionEventOutbox outboxEvent = TransactionEventOutbox.create(transaction.getTransactionId(),
                TransactionEventType.TRANSACTION_COMPLETED, toJson(payload));
        outboxRepository.save(outboxEvent);
        auditRelatedCreated("OUTBOX_EVENT_CREATED", transaction, "OUTBOX_EVENT",
                outboxEvent.getEventId(), "Transaction-completed outbox event created", outboxValues(outboxEvent));
        auditTransactionChange("TRANSACTION_COMPLETED", transaction, previousValues, "Transaction completed");
    }

    private void completeAdjustment(BankTransaction transaction, String accountId,
                                    AccountAdjustmentResponse adjustment) {
        Map<String, Object> previousValues = transactionValues(transaction);
        transaction.setTransactionStatus(TransactionStatus.COMPLETED);
        transaction.setCompletedAt(LocalDateTime.now());

        StatementEntryType entryType = isDeposit(transaction)
                ? StatementEntryType.CREDIT : StatementEntryType.DEBIT;
        String holder = customerName(adjustment.customerId());
        String operation = isDeposit(transaction)
                ? (transaction.getTransactionType() == TransactionType.FIXED_DEPOSIT_FUNDING
                    ? "FIXED DEPOSIT FUNDING BY "
                    : transaction.getTransactionType() == TransactionType.OPENING_DEPOSIT
                        ? "OPENING DEPOSIT BY " : "DEPOSIT BY ")
                : "WITHDRAWAL BY ";
        String description = operation + holder + " | REF " + transaction.getTransactionRef();
        transaction.setDescription(limit(operation + holder, 500));
        AccountStatement statementEntry = statement(transaction, accountId, entryType,
                adjustment.balanceAfter(), description);
        statementRepository.save(statementEntry);
        ledgerService.postCompletedTransaction(transaction);
        auditRelatedCreated("STATEMENT_ENTRY_CREATED", transaction, "STATEMENT",
                statementEntry.getStatementId(), entryType + " statement entry created",
                statementValues(statementEntry));

        Map<String, Object> payload = basePayload(transaction);
        payload.put("accountId", accountId);
        payload.put("balanceAfter", adjustment.balanceAfter());
        TransactionEventOutbox outboxEvent = TransactionEventOutbox.create(transaction.getTransactionId(),
                TransactionEventType.TRANSACTION_COMPLETED, toJson(payload));
        outboxRepository.save(outboxEvent);
        auditRelatedCreated("OUTBOX_EVENT_CREATED", transaction, "OUTBOX_EVENT",
                outboxEvent.getEventId(), "Transaction-completed outbox event created", outboxValues(outboxEvent));
        auditTransactionChange("TRANSACTION_COMPLETED", transaction, previousValues, "Transaction completed");
    }

    private String postingAccountId(BankTransaction transaction) {
        return isDeposit(transaction)
                ? transaction.getCreditAccountId() : transaction.getDebitAccountId();
    }

    private AccountAdjustmentRequest.AdjustmentType adjustmentType(BankTransaction transaction) {
        return isDeposit(transaction) ? AccountAdjustmentRequest.AdjustmentType.DEPOSIT
                : AccountAdjustmentRequest.AdjustmentType.WITHDRAWAL;
    }

    private boolean isDeposit(BankTransaction transaction) {
        return transaction.getTransactionType() == TransactionType.DEPOSIT
                || transaction.getTransactionType() == TransactionType.OPENING_DEPOSIT
                || transaction.getTransactionType() == TransactionType.FIXED_DEPOSIT_FUNDING;
    }

    private void fail(BankTransaction transaction, AccountPostingException exception) {
        Map<String, Object> previousValues = transactionValues(transaction);
        transaction.setTransactionStatus(TransactionStatus.FAILED);
        transaction.setFailureCode(limit(exception.getFailureCode(), 50));
        transaction.setFailureReason(limit(exception.getMessage(), 500));
        Map<String, Object> payload = basePayload(transaction);
        payload.put("failureCode", transaction.getFailureCode());
        payload.put("failureReason", transaction.getFailureReason());
        TransactionEventOutbox outboxEvent = TransactionEventOutbox.create(transaction.getTransactionId(),
                TransactionEventType.TRANSACTION_FAILED, toJson(payload));
        outboxRepository.save(outboxEvent);
        auditRelatedCreated("OUTBOX_EVENT_CREATED", transaction, "OUTBOX_EVENT",
                outboxEvent.getEventId(), "Transaction-failed outbox event created", outboxValues(outboxEvent));
        Map<String, Object> details = transactionAuditDetails(transaction);
        details.put("previousStatus", TransactionStatus.PROCESSING.name());
        details.put("newStatus", TransactionStatus.FAILED.name());
        details.put("failureReason", transaction.getFailureReason());
        putChanges(details, previousValues, transactionValues(transaction));
        auditClient.failed("transactions", "TRANSACTION_FAILED", "Transaction failed",
                transaction.getFailureCode(), transaction.getFailureReason(), details);
    }

    private AccountStatement statement(BankTransaction transaction, String accountId,
                                       StatementEntryType entryType, BigDecimal balanceAfter,
                                       String description) {
        AccountStatement statement = new AccountStatement();
        statement.setTransaction(transaction);
        statement.setAccountId(accountId);
        statement.setEntryType(entryType);
        statement.setAmount(transaction.getAmount());
        statement.setDescription(limit(description, 500));
        statement.setWithdrawalAmount(entryType == StatementEntryType.DEBIT ? transaction.getAmount() : null);
        statement.setDepositAmount(entryType == StatementEntryType.CREDIT ? transaction.getAmount() : null);
        statement.setCurrencyCode(transaction.getCurrencyCode());
        statement.setBalanceAfter(balanceAfter);
        statement.setClosingBalance(balanceAfter);
        return statement;
    }

    private String customerName(String customerId) {
        return customersClient.displayName(customerId).trim().toUpperCase(Locale.ROOT);
    }

    private boolean isSelfTransfer(BankTransaction transaction) {
        return !isBlank(transaction.getInitiatedByCustomerId());
    }

    private String maskedAccount(String accountNumber, String accountId) {
        String value = isBlank(accountNumber) ? accountId : accountNumber;
        if (isBlank(value)) return "XX";
        String lastFour = value.length() <= 4 ? value : value.substring(value.length() - 4);
        return "XX" + lastFour;
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
        payload.put("description", transaction.getDescription());
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

    private void auditTransactionChange(String action, BankTransaction transaction,
                                        Map<String, ?> previousValues, String description) {
        Map<String, Object> changes = auditClient.changes(previousValues, transactionValues(transaction));
        if (changes != null && changes.isEmpty()) return;
        Map<String, Object> details = transactionAuditDetails(transaction);
        details.put("previousStatus", previousValues.get("transactionStatus"));
        details.put("newStatus", transaction.getTransactionStatus().name());
        if (changes != null) {
            details.putAll(changes);
            if (!previousValues.isEmpty()) description += ": " + changes.get("changedFields");
        }
        auditClient.success("transactions", action, description, details);
    }

    private void auditRelatedCreated(String action, BankTransaction transaction, String relatedType,
                                     String relatedId, String description, Map<String, ?> newValues) {
        Map<String, Object> details = transactionAuditDetails(transaction);
        details.put("relatedEntityType", relatedType);
        details.put("relatedEntityId", relatedId);
        putChanges(details, Map.of(), newValues);
        auditClient.success("transactions", action, description, details);
    }

    private Map<String, Object> transactionAuditDetails(BankTransaction transaction) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("transactionId", transaction.getTransactionId());
        details.put("transactionRef", transaction.getTransactionRef());
        details.put("debitAccountId", transaction.getDebitAccountId());
        details.put("creditAccountId", transaction.getCreditAccountId());
        details.put("amount", transaction.getAmount());
        details.put("currencyCode", transaction.getCurrencyCode());
        return details;
    }

    private Map<String, Object> transactionValues(BankTransaction transaction) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("transactionRef", transaction.getTransactionRef());
        values.put("transactionType", transaction.getTransactionType() == null ? null : transaction.getTransactionType().name());
        values.put("transactionStatus", transaction.getTransactionStatus() == null ? null : transaction.getTransactionStatus().name());
        values.put("debitAccountId", transaction.getDebitAccountId());
        values.put("creditAccountId", transaction.getCreditAccountId());
        values.put("externalBeneficiary", transaction.getExternalBeneficiary());
        values.put("description", transaction.getDescription());
        values.put("amount", transaction.getAmount());
        values.put("currencyCode", transaction.getCurrencyCode());
        values.put("feeAmount", transaction.getFeeAmount());
        values.put("initiatedByCustomerId", transaction.getInitiatedByCustomerId());
        values.put("initiatedByUserId", transaction.getInitiatedByUserId());
        values.put("completedAt", transaction.getCompletedAt());
        values.put("failureCode", transaction.getFailureCode());
        values.put("failureReason", transaction.getFailureReason());
        return values;
    }

    private Map<String, Object> statementValues(AccountStatement statement) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("accountId", statement.getAccountId());
        values.put("entryType", statement.getEntryType().name());
        values.put("amount", statement.getAmount());
        values.put("description", statement.getDescription());
        values.put("withdrawalAmount", statement.getWithdrawalAmount());
        values.put("depositAmount", statement.getDepositAmount());
        values.put("currencyCode", statement.getCurrencyCode());
        values.put("balanceAfter", statement.getBalanceAfter());
        values.put("closingBalance", statement.getClosingBalance());
        return values;
    }

    private Map<String, Object> outboxValues(TransactionEventOutbox event) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("aggregateId", event.getAggregateId());
        values.put("eventType", event.getEventType().name());
        values.put("retryCount", event.getRetryCount());
        values.put("publishedAt", event.getPublishedAt());
        return values;
    }

    private void putChanges(Map<String, Object> details, Map<String, ?> previousValues,
                            Map<String, ?> newValues) {
        Map<String, Object> changes = auditClient.changes(previousValues, newValues);
        if (changes != null) details.putAll(changes);
    }

    private boolean isBlank(String value) { return value == null || value.isBlank(); }
}
