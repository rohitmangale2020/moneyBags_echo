package com.training.platform.accounts.service;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AccountService {
    private final AccountRepository accountRepository;
    private final AccountHolderRepository accountHolderRepository;
    private final AccountStatusHistoryRepository accountStatusHistoryRepository;
    private final AccountTransferOperationRepository transferOperationRepository;
    private final AccountBalanceOperationRepository balanceOperationRepository;

    public AccountService(AccountRepository accountRepository, AccountHolderRepository accountHolderRepository,
                          AccountStatusHistoryRepository accountStatusHistoryRepository,
                          AccountTransferOperationRepository transferOperationRepository,
                          AccountBalanceOperationRepository balanceOperationRepository) {
        this.accountRepository = accountRepository;
        this.accountHolderRepository = accountHolderRepository;
        this.accountStatusHistoryRepository = accountStatusHistoryRepository;
        this.transferOperationRepository = transferOperationRepository;
        this.balanceOperationRepository = balanceOperationRepository;
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
        existing.setClosedAt(account.getClosedAt());
        existing.setUpdatedByUserId(account.getUpdatedByUserId());
        validate(existing);
        return accountRepository.save(existing);
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

        BigDecimal debitBalanceAfter = debitAccount.getAvailableBalance().subtract(request.amount());
        BigDecimal creditBalanceAfter = creditAccount.getAvailableBalance().add(request.amount());
        debitAccount.setAvailableBalance(debitBalanceAfter);
        creditAccount.setAvailableBalance(creditBalanceAfter);
        accountRepository.saveAll(List.of(debitAccount, creditAccount));

        AccountTransferOperation operation = AccountTransferOperation.completed(
                request.transactionRef(), request.debitAccountId(), request.creditAccountId(),
                request.customerId(), request.amount(), currencyCode, debitBalanceAfter, creditBalanceAfter);
        transferOperationRepository.save(operation);
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
        return new AccountAdjustmentResponse(operation.getTransactionRef(), operation.getAccountId(),
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
        return new AccountTransferResponse(operation.getTransactionRef(), operation.getDebitAccountId(),
                operation.getCreditAccountId(), operation.getDebitBalanceAfter(),
                operation.getCreditBalanceAfter(), operation.getProcessedAt());
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

    private boolean isBlank(String value) { return value == null || value.isBlank(); }
}
