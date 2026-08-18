package com.training.platform.transactions.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.training.platform.auditclient.AuditClient;
import com.training.platform.transactions.client.AccountPostingException;
import com.training.platform.transactions.client.AccountAdjustmentRequest;
import com.training.platform.transactions.client.AccountAdjustmentResponse;
import com.training.platform.transactions.client.AccountTransferRequest;
import com.training.platform.transactions.client.AccountTransferResponse;
import com.training.platform.transactions.client.AccountTransferRequest.TransferPurpose;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
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
            if (existing.getTransactionStatus() != TransactionStatus.FAILED) return existing;
            transaction = existing;
        }

        transaction.setTransactionStatus(TransactionStatus.PROCESSING);
        transaction.setCompletedAt(null);
        transaction.setDescription(limit(initialDescription(transaction), 500));
        transaction.setFailureCode(null);
        transaction.setFailureReason(null);
        BankTransaction persisted = transactionRepository.saveAndFlush(transaction);
        auditTransactionChange("TRANSACTION_INITIATED", persisted, Map.of(),
                initiatedAuditDescription(persisted));

        try {
            if (isTransferPosting(persisted.getTransactionType())) {
                AccountTransferResponse transfer = accountsClient.transfer(new AccountTransferRequest(
                        persisted.getTransactionRef(), persisted.getDebitAccountId(), persisted.getCreditAccountId(),
                        persisted.getAmount(), persisted.getCurrencyCode(), persisted.getInitiatedByCustomerId(),
                        transferPurpose(persisted.getTransactionType())));
                validateTransferResponse(persisted, transfer);
                completeTransfer(persisted, transfer);
            } else {
                String accountId = postingAccountId(persisted);
                AccountAdjustmentRequest.AdjustmentType adjustmentType = adjustmentType(persisted);
                AccountAdjustmentResponse adjustment = accountsClient.adjust(accountId,
                        new AccountAdjustmentRequest(persisted.getTransactionRef(), adjustmentType,
                                persisted.getAmount(), persisted.getCurrencyCode(),
                                persisted.getInterestPeriodEnd()));
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
        if (!isTransferPosting(transaction.getTransactionType())
                && transaction.getTransactionType() != TransactionType.OPENING_DEPOSIT
                && transaction.getTransactionType() != TransactionType.DEPOSIT
                && transaction.getTransactionType() != TransactionType.WITHDRAWAL
                && transaction.getTransactionType() != TransactionType.MONTHLY_MAINTENANCE_FEE
                && transaction.getTransactionType() != TransactionType.ANNUAL_MAINTENANCE_FEE
                && transaction.getTransactionType() != TransactionType.INTEREST_CREDIT
                && transaction.getTransactionType() != TransactionType.FIXED_DEPOSIT_INTEREST_CREDIT) {
            throw new IllegalArgumentException("Transaction type does not have a supported account posting");
        }
        if (isBlank(transaction.getTransactionRef())) {
            throw new IllegalArgumentException("Transaction reference is required");
        }
        if (isTransferPosting(transaction.getTransactionType())) {
            if (isBlank(transaction.getDebitAccountId()) || isBlank(transaction.getCreditAccountId())) {
                throw new IllegalArgumentException("Debit and credit account IDs are required");
            }
            if (transaction.getDebitAccountId().equals(transaction.getCreditAccountId())) {
                throw new IllegalArgumentException("Debit and credit accounts must be different");
            }
        } else if ((transaction.getTransactionType() == TransactionType.OPENING_DEPOSIT
                || transaction.getTransactionType() == TransactionType.DEPOSIT
                || transaction.getTransactionType() == TransactionType.INTEREST_CREDIT
                || transaction.getTransactionType() == TransactionType.FIXED_DEPOSIT_INTEREST_CREDIT)
                && isBlank(transaction.getCreditAccountId())) {
            throw new IllegalArgumentException("Credit account ID is required for a credit posting");
        } else if ((transaction.getTransactionType() == TransactionType.WITHDRAWAL
                || transaction.getTransactionType() == TransactionType.MONTHLY_MAINTENANCE_FEE
                || transaction.getTransactionType() == TransactionType.ANNUAL_MAINTENANCE_FEE)
                && isBlank(transaction.getDebitAccountId())) {
            throw new IllegalArgumentException("Debit account ID is required for a debit posting");
        }
        if (transaction.getAmount() == null || transaction.getAmount().signum() <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        if (isBlank(transaction.getCurrencyCode())) {
            throw new IllegalArgumentException("Currency code is required");
        }
        BigDecimal feeAmount = transaction.getFeeAmount() == null ? BigDecimal.ZERO : transaction.getFeeAmount();
        if (transaction.getTransactionType() == TransactionType.MONTHLY_MAINTENANCE_FEE
                || transaction.getTransactionType() == TransactionType.ANNUAL_MAINTENANCE_FEE) {
            if (feeAmount.compareTo(transaction.getAmount()) != 0) {
                throw new IllegalArgumentException("Maintenance fee amount must match the transaction amount");
            }
        } else if (feeAmount.signum() != 0) {
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

    private boolean isTransferPosting(TransactionType type) {
        return type == TransactionType.TRANSFER
                || type == TransactionType.FIXED_DEPOSIT_FUNDING
                || type == TransactionType.FIXED_DEPOSIT_MATURITY
                || type == TransactionType.FIXED_DEPOSIT_PREMATURE_CLOSURE;
    }

    private TransferPurpose transferPurpose(TransactionType type) {
        return switch (type) {
            case FIXED_DEPOSIT_FUNDING -> TransferPurpose.FIXED_DEPOSIT_FUNDING;
            case FIXED_DEPOSIT_MATURITY -> TransferPurpose.FIXED_DEPOSIT_MATURITY;
            case FIXED_DEPOSIT_PREMATURE_CLOSURE -> TransferPurpose.FIXED_DEPOSIT_PREMATURE_CLOSURE;
            default -> TransferPurpose.STANDARD;
        };
    }

    private void completeTransfer(BankTransaction transaction, AccountTransferResponse transfer) {
        Map<String, Object> previousValues = transactionValues(transaction);
        transaction.setTransactionStatus(TransactionStatus.COMPLETED);
        transaction.setCompletedAt(LocalDateTime.now());

        String debitHolder = customerName(transfer.debitCustomerId());
        String creditHolder = customerName(transfer.creditCustomerId());
        String channel = transferLabel(transaction);
        transaction.setDescription(limit(transferDescription(transaction, debitHolder, creditHolder), 500));

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
                debitEntry.getStatementId(), statementAuditDescription(transaction, StatementEntryType.DEBIT),
                statementValues(debitEntry));
        auditRelatedCreated("STATEMENT_ENTRY_CREATED", transaction, "STATEMENT",
                creditEntry.getStatementId(), statementAuditDescription(transaction, StatementEntryType.CREDIT),
                statementValues(creditEntry));

        Map<String, Object> payload = basePayload(transaction);
        payload.put("debitBalanceAfter", transfer.debitBalanceAfter());
        payload.put("creditBalanceAfter", transfer.creditBalanceAfter());
        TransactionEventOutbox outboxEvent = TransactionEventOutbox.create(transaction.getTransactionId(),
                TransactionEventType.TRANSACTION_COMPLETED, toJson(payload));
        outboxRepository.save(outboxEvent);
        auditRelatedCreated("OUTBOX_EVENT_CREATED", transaction, "OUTBOX_EVENT",
                outboxEvent.getEventId(), outboxAuditDescription(transaction, true), outboxValues(outboxEvent));
        auditTransactionChange("TRANSACTION_COMPLETED", transaction, previousValues,
                completedAuditDescription(transaction));
    }

    private void completeAdjustment(BankTransaction transaction, String accountId,
                                    AccountAdjustmentResponse adjustment) {
        Map<String, Object> previousValues = transactionValues(transaction);
        transaction.setTransactionStatus(TransactionStatus.COMPLETED);
        transaction.setCompletedAt(LocalDateTime.now());

        StatementEntryType entryType = transaction.getTransactionType() == TransactionType.OPENING_DEPOSIT
                || transaction.getTransactionType() == TransactionType.DEPOSIT
                || transaction.getTransactionType() == TransactionType.INTEREST_CREDIT
                || transaction.getTransactionType() == TransactionType.FIXED_DEPOSIT_INTEREST_CREDIT
                ? StatementEntryType.CREDIT : StatementEntryType.DEBIT;
        String holder = customerName(adjustment.customerId());
        String transactionDescription = adjustmentDescription(transaction, holder);
        String description = transactionDescription + " | REF " + transaction.getTransactionRef();
        transaction.setDescription(limit(transactionDescription, 500));
        AccountStatement statementEntry = statement(transaction, accountId, entryType,
                adjustment.balanceAfter(), description);
        statementRepository.save(statementEntry);
        ledgerService.postCompletedTransaction(transaction);
        auditRelatedCreated("STATEMENT_ENTRY_CREATED", transaction, "STATEMENT",
                statementEntry.getStatementId(), statementAuditDescription(transaction, entryType),
                statementValues(statementEntry));

        Map<String, Object> payload = basePayload(transaction);
        payload.put("accountId", accountId);
        payload.put("balanceAfter", adjustment.balanceAfter());
        TransactionEventOutbox outboxEvent = TransactionEventOutbox.create(transaction.getTransactionId(),
                TransactionEventType.TRANSACTION_COMPLETED, toJson(payload));
        outboxRepository.save(outboxEvent);
        auditRelatedCreated("OUTBOX_EVENT_CREATED", transaction, "OUTBOX_EVENT",
                outboxEvent.getEventId(), outboxAuditDescription(transaction, true), outboxValues(outboxEvent));
        auditTransactionChange("TRANSACTION_COMPLETED", transaction, previousValues,
                completedAuditDescription(transaction));
    }

    private String postingAccountId(BankTransaction transaction) {
        return transaction.getTransactionType() == TransactionType.OPENING_DEPOSIT
                || transaction.getTransactionType() == TransactionType.DEPOSIT
                || transaction.getTransactionType() == TransactionType.INTEREST_CREDIT
                || transaction.getTransactionType() == TransactionType.FIXED_DEPOSIT_INTEREST_CREDIT
                ? transaction.getCreditAccountId() : transaction.getDebitAccountId();
    }

    private AccountAdjustmentRequest.AdjustmentType adjustmentType(BankTransaction transaction) {
        return switch (transaction.getTransactionType()) {
            case OPENING_DEPOSIT -> AccountAdjustmentRequest.AdjustmentType.OPENING_DEPOSIT;
            case DEPOSIT -> AccountAdjustmentRequest.AdjustmentType.DEPOSIT;
            case WITHDRAWAL -> AccountAdjustmentRequest.AdjustmentType.WITHDRAWAL;
            case MONTHLY_MAINTENANCE_FEE -> AccountAdjustmentRequest.AdjustmentType.MONTHLY_MAINTENANCE_FEE;
            case ANNUAL_MAINTENANCE_FEE -> AccountAdjustmentRequest.AdjustmentType.ANNUAL_MAINTENANCE_FEE;
            case INTEREST_CREDIT -> AccountAdjustmentRequest.AdjustmentType.INTEREST_CREDIT;
            case FIXED_DEPOSIT_INTEREST_CREDIT ->
                    AccountAdjustmentRequest.AdjustmentType.FIXED_DEPOSIT_INTEREST_CREDIT;
            default -> throw new IllegalArgumentException(
                    "Transaction type does not use a single-account adjustment: "
                            + transaction.getTransactionType());
        };
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
                outboxEvent.getEventId(), outboxAuditDescription(transaction, false), outboxValues(outboxEvent));
        Map<String, Object> details = transactionAuditDetails(transaction);
        details.put("previousStatus", TransactionStatus.PROCESSING.name());
        details.put("newStatus", TransactionStatus.FAILED.name());
        details.put("failureReason", transaction.getFailureReason());
        putChanges(details, previousValues, transactionValues(transaction));
        auditClient.failed("transactions", "TRANSACTION_FAILED", failedAuditDescription(transaction),
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
        payload.put("maker", transaction.getInitiatedByUserId());
        payload.put("businessDate", businessDate(transaction));
        payload.put("completedAt", transaction.getCompletedAt());
        payload.put("interestPeriodEnd", transaction.getInterestPeriodEnd());
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
        target.setInterestPeriodEnd(source.getInterestPeriodEnd());
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
        details.put("transactionType", transaction.getTransactionType().name());
        details.put("description", transaction.getDescription());
        details.put("businessDate", businessDate(transaction));
        details.put("initiatedByUserId", transaction.getInitiatedByUserId());
        details.put("interestPeriodEnd", transaction.getInterestPeriodEnd());
        if (isSystemGenerated(transaction)) {
            details.put("actorId", "SYSTEM");
            details.put("actorType", "SYSTEM");
        }
        return details;
    }

    private String initialDescription(BankTransaction transaction) {
        LocalDate date = businessDate(transaction);
        return switch (transaction.getTransactionType()) {
            case INTEREST_CREDIT -> "Savings interest credit for period ending " + date;
            case FIXED_DEPOSIT_INTEREST_CREDIT ->
                    "Fixed-deposit interest credit for period ending " + date;
            case MONTHLY_MAINTENANCE_FEE ->
                    "Monthly maintenance fee for " + YearMonth.from(date);
            case ANNUAL_MAINTENANCE_FEE ->
                    "Annual maintenance fee for the " + annualFeeYear(transaction) + " account anniversary";
            case FIXED_DEPOSIT_FUNDING -> "Fixed-deposit principal funding";
            case FIXED_DEPOSIT_MATURITY -> "Fixed-deposit principal payout on maturity";
            case FIXED_DEPOSIT_PREMATURE_CLOSURE -> "Fixed-deposit principal return after premature closure";
            case OPENING_DEPOSIT -> "Opening deposit";
            case DEPOSIT -> "Account deposit";
            case WITHDRAWAL -> "Account withdrawal";
            default -> isSelfTransfer(transaction) ? "Self transfer" : "Internal transfer";
        };
    }

    private String adjustmentDescription(BankTransaction transaction, String holder) {
        LocalDate date = businessDate(transaction);
        return switch (transaction.getTransactionType()) {
            case OPENING_DEPOSIT -> "Opening deposit by " + holder;
            case DEPOSIT -> "Deposit by " + holder;
            case INTEREST_CREDIT ->
                    "Savings interest credited to " + holder + " for period ending " + date;
            case FIXED_DEPOSIT_INTEREST_CREDIT ->
                    "Fixed-deposit interest credited to " + holder + " for period ending " + date;
            case MONTHLY_MAINTENANCE_FEE ->
                    "Monthly maintenance fee charged to " + holder + " for " + YearMonth.from(date);
            case ANNUAL_MAINTENANCE_FEE ->
                    "Annual maintenance fee charged to " + holder + " for the "
                            + annualFeeYear(transaction) + " account anniversary";
            default -> "Withdrawal by " + holder;
        };
    }

    private String transferLabel(BankTransaction transaction) {
        return switch (transaction.getTransactionType()) {
            case FIXED_DEPOSIT_FUNDING -> "FIXED DEPOSIT FUNDING";
            case FIXED_DEPOSIT_MATURITY -> "FIXED DEPOSIT MATURITY";
            case FIXED_DEPOSIT_PREMATURE_CLOSURE -> "FIXED DEPOSIT PREMATURE CLOSURE";
            default -> isSelfTransfer(transaction) ? "SELF TRANSFER" : "INTERNAL TRANSFER";
        };
    }

    private String transferDescription(BankTransaction transaction, String debitHolder, String creditHolder) {
        return switch (transaction.getTransactionType()) {
            case FIXED_DEPOSIT_FUNDING ->
                    "Fixed-deposit principal funded from " + debitHolder + " to " + creditHolder;
            case FIXED_DEPOSIT_MATURITY ->
                    "Fixed-deposit principal paid on maturity from " + debitHolder + " to " + creditHolder;
            case FIXED_DEPOSIT_PREMATURE_CLOSURE ->
                    "Fixed-deposit principal returned after premature closure from "
                            + debitHolder + " to " + creditHolder;
            default -> transferLabel(transaction) + " FROM " + debitHolder + " TO " + creditHolder;
        };
    }

    private String initiatedAuditDescription(BankTransaction transaction) {
        return switch (transaction.getTransactionType()) {
            case INTEREST_CREDIT -> "Savings interest posting initiated for period ending "
                    + businessDate(transaction);
            case FIXED_DEPOSIT_INTEREST_CREDIT ->
                    "Fixed-deposit interest posting initiated for period ending " + businessDate(transaction);
            case MONTHLY_MAINTENANCE_FEE -> "Monthly maintenance fee posting initiated for "
                    + YearMonth.from(businessDate(transaction));
            case ANNUAL_MAINTENANCE_FEE -> "Annual maintenance fee posting initiated for the "
                    + annualFeeYear(transaction) + " account anniversary";
            case FIXED_DEPOSIT_MATURITY -> "Fixed-deposit maturity payout initiated";
            default -> "Transaction initiated";
        };
    }

    private String completedAuditDescription(BankTransaction transaction) {
        return switch (transaction.getTransactionType()) {
            case INTEREST_CREDIT -> "Savings interest credited for period ending " + businessDate(transaction);
            case FIXED_DEPOSIT_INTEREST_CREDIT ->
                    "Fixed-deposit interest credited for period ending " + businessDate(transaction);
            case MONTHLY_MAINTENANCE_FEE -> "Monthly maintenance fee charged for "
                    + YearMonth.from(businessDate(transaction));
            case ANNUAL_MAINTENANCE_FEE -> "Annual maintenance fee charged for the "
                    + annualFeeYear(transaction) + " account anniversary";
            case FIXED_DEPOSIT_MATURITY -> "Fixed-deposit principal paid on maturity";
            case FIXED_DEPOSIT_PREMATURE_CLOSURE ->
                    "Fixed-deposit principal returned after premature closure";
            case FIXED_DEPOSIT_FUNDING -> "Fixed-deposit principal funded";
            default -> "Transaction completed: " + transaction.getDescription();
        };
    }

    private String failedAuditDescription(BankTransaction transaction) {
        return switch (transaction.getTransactionType()) {
            case INTEREST_CREDIT -> "Savings interest posting failed for period ending "
                    + businessDate(transaction);
            case FIXED_DEPOSIT_INTEREST_CREDIT ->
                    "Fixed-deposit interest posting failed for period ending " + businessDate(transaction);
            case MONTHLY_MAINTENANCE_FEE -> "Monthly maintenance fee posting failed for "
                    + YearMonth.from(businessDate(transaction));
            case ANNUAL_MAINTENANCE_FEE -> "Annual maintenance fee posting failed for the "
                    + annualFeeYear(transaction) + " account anniversary";
            default -> "Transaction failed: " + initialDescription(transaction);
        };
    }

    private String statementAuditDescription(BankTransaction transaction, StatementEntryType entryType) {
        return switch (transaction.getTransactionType()) {
            case INTEREST_CREDIT -> "Savings interest credit statement entry created for period ending "
                    + businessDate(transaction);
            case FIXED_DEPOSIT_INTEREST_CREDIT ->
                    "Fixed-deposit interest credit statement entry created for period ending "
                            + businessDate(transaction);
            case MONTHLY_MAINTENANCE_FEE -> "Monthly maintenance fee debit statement entry created for "
                    + YearMonth.from(businessDate(transaction));
            case ANNUAL_MAINTENANCE_FEE ->
                    "Annual maintenance fee debit statement entry created for the "
                            + annualFeeYear(transaction) + " account anniversary";
            default -> entryType + " statement entry created for " + initialDescription(transaction);
        };
    }

    private String outboxAuditDescription(BankTransaction transaction, boolean completed) {
        String outcome = completed ? "completion" : "failure";
        return switch (transaction.getTransactionType()) {
            case INTEREST_CREDIT -> "Savings interest " + outcome
                    + " event queued for period ending " + businessDate(transaction);
            case FIXED_DEPOSIT_INTEREST_CREDIT -> "Fixed-deposit interest " + outcome
                    + " event queued for period ending " + businessDate(transaction);
            case MONTHLY_MAINTENANCE_FEE -> "Monthly maintenance fee " + outcome
                    + " event queued for " + YearMonth.from(businessDate(transaction));
            case ANNUAL_MAINTENANCE_FEE -> "Annual maintenance fee " + outcome
                    + " event queued for the " + annualFeeYear(transaction) + " account anniversary";
            default -> "Transaction " + outcome + " event queued";
        };
    }

    private LocalDate businessDate(BankTransaction transaction) {
        if (transaction.getInterestPeriodEnd() != null) return transaction.getInterestPeriodEnd();
        if (transaction.getCompletedAt() != null) return transaction.getCompletedAt().toLocalDate();
        if (transaction.getInitiatedAt() != null) return transaction.getInitiatedAt().toLocalDate();
        return LocalDate.now();
    }

    private int annualFeeYear(BankTransaction transaction) {
        String reference = transaction.getTransactionRef();
        if (reference != null && reference.matches("AF\\d{4}-.+")) {
            return Integer.parseInt(reference.substring(2, 6));
        }
        return businessDate(transaction).getYear();
    }

    private boolean isSystemGenerated(BankTransaction transaction) {
        if (!"SYSTEM".equalsIgnoreCase(transaction.getInitiatedByUserId())) return false;
        return transaction.getTransactionType() == TransactionType.INTEREST_CREDIT
                || transaction.getTransactionType() == TransactionType.FIXED_DEPOSIT_INTEREST_CREDIT
                || transaction.getTransactionType() == TransactionType.MONTHLY_MAINTENANCE_FEE
                || transaction.getTransactionType() == TransactionType.ANNUAL_MAINTENANCE_FEE
                || transaction.getTransactionType() == TransactionType.FIXED_DEPOSIT_MATURITY;
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
        values.put("interestPeriodEnd", transaction.getInterestPeriodEnd());
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
