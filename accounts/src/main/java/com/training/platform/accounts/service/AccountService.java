package com.training.platform.accounts.service;

import com.training.platform.auditclient.AuditClient;
import com.training.platform.accounts.dto.AccountTransferRequest;
import com.training.platform.accounts.dto.AccountTransferResponse;
import com.training.platform.accounts.dto.AccountAdjustmentRequest;
import com.training.platform.accounts.dto.AccountAdjustmentResponse;
import com.training.platform.accounts.entity.Account;
import com.training.platform.accounts.entity.AccountBalanceOperation;
import com.training.platform.accounts.entity.AccountHolder;
import com.training.platform.accounts.entity.AccountStatus;
import com.training.platform.accounts.entity.AccountStatusHistory;
import com.training.platform.accounts.entity.AccountTransferOperation;
import com.training.platform.accounts.repository.AccountHolderRepository;
import com.training.platform.accounts.repository.AccountBalanceOperationRepository;
import com.training.platform.accounts.repository.AccountRepository;
import com.training.platform.accounts.repository.AccountStatusHistoryRepository;
import com.training.platform.accounts.repository.AccountTransferOperationRepository;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AccountService {
    private static final int ACCOUNT_NUMBER_LENGTH = 12;
    private static final long ACCOUNT_NUMBER_BOUND = 1_000_000_000_000L;
    private static final int ACCOUNT_NUMBER_ATTEMPTS = 20;
    private static final SecureRandom ACCOUNT_NUMBER_RANDOM = new SecureRandom();
    private final AccountRepository accountRepository;
    private final AccountHolderRepository accountHolderRepository;
    private final AccountStatusHistoryRepository accountStatusHistoryRepository;
    private final AccountTransferOperationRepository transferOperationRepository;
    private final AccountBalanceOperationRepository balanceOperationRepository;
    private final AuditClient auditClient;

    public AccountService(AccountRepository accountRepository, AccountHolderRepository accountHolderRepository,
                          AccountStatusHistoryRepository accountStatusHistoryRepository,
                          AccountTransferOperationRepository transferOperationRepository,
                          AccountBalanceOperationRepository balanceOperationRepository,
                          AuditClient auditClient) {
        this.accountRepository = accountRepository;
        this.accountHolderRepository = accountHolderRepository;
        this.accountStatusHistoryRepository = accountStatusHistoryRepository;
        this.transferOperationRepository = transferOperationRepository;
        this.balanceOperationRepository = balanceOperationRepository;
        this.auditClient = auditClient;
    }

    public Account getById(String accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new EntityNotFoundException("Account not found: " + accountId));
    }

    public Account getByAccountNumber(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new EntityNotFoundException("Account not found: " + accountNumber));
    }

    public List<Account> getAllAccounts() {
        return accountRepository.findAllByOrderByCreatedAtDesc();
    }

    public Page<Account> getAccounts(Pageable pageable) {
        return accountRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    public List<Account> getByCustomerId(String customerId) { return accountRepository.findByCustomerId(customerId); }

    @Transactional
    public Account create(Account account) {
        account.setAccountNumber(generateAccountNumber());
        validate(account);
        Account savedAccount = accountRepository.save(account);
        accountHolderRepository.save(AccountHolder.primaryHolder(savedAccount));
        accountStatusHistoryRepository.save(AccountStatusHistory.initialStatus(savedAccount));
        Map<String, Object> details = accountDetails(savedAccount);
        details.put("newStatus", savedAccount.getStatus().name());
        details.put("balanceAfter", savedAccount.getAvailableBalance());
        putChanges(details, Map.of(), accountValues(savedAccount));
        auditClient.success("accounts", "ACCOUNT_OPENED", "Account opened", details);
        Map<String, Object> holderDetails = accountDetails(savedAccount);
        Map<String, Object> holderValues = new LinkedHashMap<>();
        holderValues.put("customerId", savedAccount.getCustomerId());
        holderValues.put("holderRole", "PRIMARY");
        holderValues.put("operatingRule", "SOLELY");
        holderValues.put("signingAuthority", "AUTHORIZED");
        holderValues.put("holderStatus", "ACTIVE");
        putChanges(holderDetails, Map.of(), holderValues);
        auditClient.success("accounts", "ACCOUNT_HOLDER_ADDED", "Primary account holder added", holderDetails);
        return savedAccount;
    }

    @Transactional
    public Account update(String accountId, Account account) {
        Account existing = getById(accountId);
        Map<String, Object> previousValues = accountValues(existing);
        AccountStatus previousStatus = existing.getStatus();
        BigDecimal previousBalance = existing.getAvailableBalance();
        // Account numbers are immutable. This also preserves legacy, non-numeric
        // numbers while every newly opened account uses the numeric format.
        existing.setCustomerId(account.getCustomerId());
        existing.setProductId(account.getProductId());
        existing.setOwnershipType(account.getOwnershipType());
        existing.setStatus(account.getStatus());
        existing.setCurrencyCode(account.getCurrencyCode());
        existing.setClosedAt(account.getClosedAt());
        existing.setUpdatedByUserId(account.getUpdatedByUserId());
        validate(existing);
        Account saved = accountRepository.save(existing);
        Map<String, Object> details = accountDetails(saved);
        details.put("previousStatus", previousStatus.name());
        details.put("newStatus", saved.getStatus().name());
        details.put("balanceBefore", previousBalance);
        details.put("balanceAfter", saved.getAvailableBalance());
        String action = previousStatus == saved.getStatus() ? "ACCOUNT_UPDATED" : "ACCOUNT_STATUS_CHANGED";
        Map<String, Object> changes = auditClient.changes(previousValues, accountValues(saved));
        if (changes == null || !changes.isEmpty()) {
            if (changes != null) details.putAll(changes);
            String description = previousStatus == saved.getStatus()
                    ? "Account fields changed" : "Account status and fields changed";
            if (changes != null) description += ": " + changes.get("changedFields");
            auditClient.success("accounts", action, description, details);
        }
        return saved;
    }

    @Transactional
    public AccountTransferResponse transfer(AccountTransferRequest request) {
        validateTransferRequest(request);
        String currencyCode = request.currencyCode().toUpperCase();

        AccountTransferOperation completed = transferOperationRepository
                .findByTransactionRef(request.transactionRef()).orElse(null);
        if (completed != null) {
            return replay(completed, request, currencyCode);
        }

        List<String> accountIds = List.of(request.debitAccountId(), request.creditAccountId()).stream()
                .sorted().toList();
        List<Account> lockedAccounts = accountRepository.findAllByIdForUpdate(accountIds);
        if (lockedAccounts.size() != 2) {
            throw new EntityNotFoundException("Both debit and credit accounts must exist");
        }

        // A retry waiting on the account locks can observe the operation saved by
        // the first request and return it without applying the balances again.
        completed = transferOperationRepository.findByTransactionRef(request.transactionRef()).orElse(null);
        if (completed != null) {
            return replay(completed, request, currencyCode);
        }

        Map<String, Account> byId = new LinkedHashMap<>();
        lockedAccounts.forEach(account -> byId.put(account.getAccountId(), account));
        Account debitAccount = byId.get(request.debitAccountId());
        Account creditAccount = byId.get(request.creditAccountId());
        if (debitAccount == null || creditAccount == null) {
            throw new EntityNotFoundException("Both debit and credit accounts must exist");
        }

        validatePostable(debitAccount, currencyCode, "Debit");
        validatePostable(creditAccount, currencyCode, "Credit");
        validateSelfTransferOwnership(request.customerId(), debitAccount, creditAccount);
        if (debitAccount.getAvailableBalance().compareTo(request.amount()) < 0) {
            throw new IllegalArgumentException("Insufficient available balance");
        }

        BigDecimal debitBalanceBefore = debitAccount.getAvailableBalance();
        BigDecimal creditBalanceBefore = creditAccount.getAvailableBalance();
        BigDecimal debitBalanceAfter = debitBalanceBefore.subtract(request.amount());
        BigDecimal creditBalanceAfter = creditBalanceBefore.add(request.amount());
        debitAccount.setAvailableBalance(debitBalanceAfter);
        creditAccount.setAvailableBalance(creditBalanceAfter);
        accountRepository.saveAll(List.of(debitAccount, creditAccount));

        AccountTransferOperation operation = AccountTransferOperation.completed(
                request.transactionRef(), request.debitAccountId(), request.creditAccountId(),
                request.customerId(), request.amount(), currencyCode, debitBalanceAfter, creditBalanceAfter);
        transferOperationRepository.save(operation);
        auditBalanceChange("BALANCE_DEBITED", debitAccount, operation.getTransactionRef(),
                operation.getOperationId(), operation.getAmount(), operation.getCurrencyCode(),
                debitBalanceBefore, debitBalanceAfter, "Transfer debit applied");
        auditBalanceChange("BALANCE_CREDITED", creditAccount, operation.getTransactionRef(),
                operation.getOperationId(), operation.getAmount(), operation.getCurrencyCode(),
                creditBalanceBefore, creditBalanceAfter, "Transfer credit applied");
        return response(operation);
    }

    private AccountTransferResponse replay(AccountTransferOperation operation,
                                            AccountTransferRequest request,
                                            String currencyCode) {
        if (!operation.matches(request.debitAccountId(), request.creditAccountId(), request.customerId(),
                request.amount(), currencyCode)) {
            throw new IllegalArgumentException("Transaction reference was already used for a different transfer");
        }
        return response(operation);
    }

    @Transactional
    public AccountAdjustmentResponse adjust(String accountId, AccountAdjustmentRequest request) {
        validateAdjustmentRequest(accountId, request);
        String currencyCode = request.currencyCode().toUpperCase();

        AccountBalanceOperation completed = balanceOperationRepository
                .findByTransactionRef(request.transactionRef()).orElse(null);
        if (completed != null) return replay(completed, accountId, request, currencyCode);

        Account account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new EntityNotFoundException("Account not found: " + accountId));
        validatePostable(account, currencyCode, "Target");

        completed = balanceOperationRepository.findByTransactionRef(request.transactionRef()).orElse(null);
        if (completed != null) return replay(completed, accountId, request, currencyCode);

        BigDecimal balanceBefore = account.getAvailableBalance();
        BigDecimal balanceAfter;
        if (request.adjustmentType() == AccountAdjustmentRequest.AdjustmentType.DEPOSIT) {
            balanceAfter = account.getAvailableBalance().add(request.amount());
        } else {
            if (account.getAvailableBalance().compareTo(request.amount()) < 0) {
                throw new IllegalArgumentException("Insufficient available balance");
            }
            balanceAfter = account.getAvailableBalance().subtract(request.amount());
        }
        account.setAvailableBalance(balanceAfter);
        accountRepository.save(account);

        AccountBalanceOperation operation = AccountBalanceOperation.completed(
                request.transactionRef(), accountId, request.adjustmentType(), request.amount(),
                currencyCode, balanceAfter);
        balanceOperationRepository.save(operation);
        String action = request.adjustmentType() == AccountAdjustmentRequest.AdjustmentType.DEPOSIT
                ? "BALANCE_CREDITED" : "BALANCE_DEBITED";
        auditBalanceChange(action, account, operation.getTransactionRef(), operation.getOperationId(),
                operation.getAmount(), operation.getCurrencyCode(), balanceBefore, balanceAfter,
                request.adjustmentType() + " applied");
        return response(operation);
    }

    private AccountAdjustmentResponse replay(AccountBalanceOperation operation, String accountId,
                                             AccountAdjustmentRequest request, String currencyCode) {
        if (!operation.matches(accountId, request.adjustmentType(), request.amount(), currencyCode)) {
            throw new IllegalArgumentException("Transaction reference was already used for a different posting");
        }
        return response(operation);
    }

    private AccountAdjustmentResponse response(AccountBalanceOperation operation) {
        Account account = getById(operation.getAccountId());
        return new AccountAdjustmentResponse(operation.getTransactionRef(), operation.getAccountId(),
                account.getAccountNumber(), account.getCustomerId(),
                operation.getAdjustmentType(), operation.getBalanceAfter(), operation.getProcessedAt());
    }

    private void validateAdjustmentRequest(String accountId, AccountAdjustmentRequest request) {
        if (isBlank(accountId)) throw new IllegalArgumentException("Account ID is required");
        if (request == null) throw new IllegalArgumentException("Adjustment request is required");
        if (isBlank(request.transactionRef())) throw new IllegalArgumentException("Transaction reference is required");
        if (request.adjustmentType() == null) throw new IllegalArgumentException("Adjustment type is required");
        if (request.amount() == null || request.amount().signum() <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        if (isBlank(request.currencyCode())) throw new IllegalArgumentException("Currency code is required");
    }

    private void validateSelfTransferOwnership(String customerId, Account debitAccount, Account creditAccount) {
        if (isBlank(customerId)) return;
        if (!customerId.equals(debitAccount.getCustomerId()) || !customerId.equals(creditAccount.getCustomerId())) {
            throw new IllegalArgumentException("Self transfer accounts must belong to customer " + customerId);
        }
    }

    private AccountTransferResponse response(AccountTransferOperation operation) {
        Account debitAccount = getById(operation.getDebitAccountId());
        Account creditAccount = getById(operation.getCreditAccountId());
        return new AccountTransferResponse(operation.getTransactionRef(), operation.getDebitAccountId(),
                operation.getCreditAccountId(), debitAccount.getAccountNumber(), creditAccount.getAccountNumber(),
                debitAccount.getCustomerId(), creditAccount.getCustomerId(), operation.getDebitBalanceAfter(),
                operation.getCreditBalanceAfter(), operation.getProcessedAt());
    }

    private String generateAccountNumber() {
        for (int attempt = 0; attempt < ACCOUNT_NUMBER_ATTEMPTS; attempt++) {
            String candidate = String.format("%0" + ACCOUNT_NUMBER_LENGTH + "d",
                    ACCOUNT_NUMBER_RANDOM.nextLong(ACCOUNT_NUMBER_BOUND));
            if (!accountRepository.existsByAccountNumber(candidate)) return candidate;
        }
        throw new IllegalStateException("Unable to generate a unique account number");
    }

    private void validateTransferRequest(AccountTransferRequest request) {
        if (request == null) throw new IllegalArgumentException("Transfer request is required");
        if (isBlank(request.transactionRef())) throw new IllegalArgumentException("Transaction reference is required");
        if (isBlank(request.debitAccountId())) throw new IllegalArgumentException("Debit account ID is required");
        if (isBlank(request.creditAccountId())) throw new IllegalArgumentException("Credit account ID is required");
        if (request.debitAccountId().equals(request.creditAccountId())) {
            throw new IllegalArgumentException("Debit and credit accounts must be different");
        }
        if (request.amount() == null || request.amount().signum() <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        if (isBlank(request.currencyCode())) throw new IllegalArgumentException("Currency code is required");
    }

    private void validatePostable(Account account, String currencyCode, String side) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalArgumentException(side + " account must be ACTIVE");
        }
        if (!account.getCurrencyCode().equalsIgnoreCase(currencyCode)) {
            throw new IllegalArgumentException(side + " account currency does not match the transaction currency");
        }
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

    private Map<String, Object> accountDetails(Account account) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("accountId", account.getAccountId());
        details.put("customerId", account.getCustomerId());
        details.put("currencyCode", account.getCurrencyCode());
        return details;
    }

    private void auditBalanceChange(String action, Account account, String transactionRef,
                                    String operationId, BigDecimal amount, String currencyCode,
                                    BigDecimal balanceBefore, BigDecimal balanceAfter, String reason) {
        Map<String, Object> details = accountDetails(account);
        details.put("transactionRef", transactionRef);
        details.put("operationId", operationId);
        details.put("amount", amount);
        details.put("currencyCode", currencyCode);
        details.put("balanceBefore", balanceBefore);
        details.put("balanceAfter", balanceAfter);
        details.put("reason", reason);
        putChanges(details, Map.of("availableBalance", balanceBefore),
                Map.of("availableBalance", balanceAfter));
        auditClient.success("accounts", action, reason, details);
    }

    private Map<String, Object> accountValues(Account account) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("accountNumber", account.getAccountNumber());
        values.put("customerId", account.getCustomerId());
        values.put("productId", account.getProductId());
        values.put("ownershipType", account.getOwnershipType() == null ? null : account.getOwnershipType().name());
        values.put("status", account.getStatus() == null ? null : account.getStatus().name());
        values.put("currencyCode", account.getCurrencyCode());
        values.put("availableBalance", account.getAvailableBalance());
        values.put("closedAt", account.getClosedAt());
        return values;
    }

    private void putChanges(Map<String, Object> details, Map<String, ?> previousValues,
                            Map<String, ?> newValues) {
        Map<String, Object> changes = auditClient.changes(previousValues, newValues);
        if (changes != null) details.putAll(changes);
    }

    private boolean isBlank(String value) { return value == null || value.isBlank(); }
}
