package com.training.platform.accounts.service;

import com.training.platform.auditclient.AuditClient;
import com.training.platform.accounts.client.ProductRulesResponse;
import com.training.platform.accounts.client.ProductsClient;
import com.training.platform.accounts.client.FixedDepositsClient;
import com.training.platform.accounts.dto.AnnualFeeAccountResponse;
import com.training.platform.accounts.dto.AccountTransferRequest;
import com.training.platform.accounts.dto.AccountTransferResponse;
import com.training.platform.accounts.dto.TransferPurpose;
import com.training.platform.accounts.dto.AccountAdjustmentRequest;
import com.training.platform.accounts.dto.AccountAdjustmentResponse;
import com.training.platform.accounts.entity.Account;
import com.training.platform.accounts.entity.AccountBalanceOperation;
import com.training.platform.accounts.entity.AccountHolder;
import com.training.platform.accounts.entity.AccountStatus;
import com.training.platform.accounts.entity.OwnershipType;
import com.training.platform.accounts.entity.AccountStatusHistory;
import com.training.platform.accounts.entity.AccountTransferOperation;
import com.training.platform.accounts.repository.AccountHolderRepository;
import com.training.platform.accounts.repository.AccountBalanceOperationRepository;
import com.training.platform.accounts.repository.AccountRepository;
import com.training.platform.accounts.repository.AccountStatusHistoryRepository;
import com.training.platform.accounts.repository.AccountTransferOperationRepository;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
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
    private final ProductsClient productsClient;
    private final FixedDepositsClient fixedDepositsClient;

    public AccountService(AccountRepository accountRepository, AccountHolderRepository accountHolderRepository,
                          AccountStatusHistoryRepository accountStatusHistoryRepository,
                          AccountTransferOperationRepository transferOperationRepository,
                          AccountBalanceOperationRepository balanceOperationRepository,
                          AuditClient auditClient,
                          ProductsClient productsClient,
                          FixedDepositsClient fixedDepositsClient) {
        this.accountRepository = accountRepository;
        this.accountHolderRepository = accountHolderRepository;
        this.accountStatusHistoryRepository = accountStatusHistoryRepository;
        this.transferOperationRepository = transferOperationRepository;
        this.balanceOperationRepository = balanceOperationRepository;
        this.auditClient = auditClient;
        this.productsClient = productsClient;
        this.fixedDepositsClient = fixedDepositsClient;
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

    public Page<Account> getAccounts(Pageable pageable, String customerId, AccountStatus status,
                                     OwnershipType ownershipType, String currencyCode) {
        Specification<Account> specification = Specification.where(null);
        if (!isBlank(customerId)) {
            specification = specification.and((root, query, builder) ->
                    builder.equal(root.get("customerId"), customerId));
        }
        if (status != null) {
            specification = specification.and((root, query, builder) ->
                    builder.equal(root.get("status"), status));
        }
        if (ownershipType != null) {
            specification = specification.and((root, query, builder) ->
                    builder.equal(root.get("ownershipType"), ownershipType));
        }
        if (!isBlank(currencyCode)) {
            specification = specification.and((root, query, builder) ->
                    builder.equal(root.get("currencyCode"), currencyCode.toUpperCase()));
        }
        return accountRepository.findAll(specification, pageable);
    }

    public List<Account> getByCustomerId(String customerId) { return accountRepository.findByCustomerId(customerId); }

    public List<Account> interestDue(LocalDate asOf) {
        if (asOf == null) throw new IllegalArgumentException("Interest processing date is required");
        return accountRepository.findInterestDue(asOf);
    }

    public List<AnnualFeeAccountResponse> annualFeeAccounts() {
        Map<String, ProductRulesResponse> products = new HashMap<>();
        return accountRepository.findActiveAnnualFeeAccounts().stream()
                .map(account -> {
                    ProductRulesResponse product = products.computeIfAbsent(account.getProductId(),
                            productsClient::getById);
                    BigDecimal fee = product.fee() == null || product.fee().annualMaintenanceFee() == null
                            ? BigDecimal.ZERO : product.fee().annualMaintenanceFee();
                    return AnnualFeeAccountResponse.from(account, fee);
                })
                .filter(account -> account.annualMaintenanceFee().signum() > 0)
                .toList();
    }

    @Transactional
    public int backfillMissingProductRules() {
        Map<String, ProductRulesResponse> products = new HashMap<>();
        List<Account> changed = accountRepository.findAll().stream()
                .filter(account -> account.getProductTypeCode() == null)
                .peek(account -> applyProductRules(account, products.computeIfAbsent(account.getProductId(),
                        productsClient::getById)))
                .toList();
        accountRepository.saveAll(changed);
        return changed.size();
    }

    @Transactional
    public Account markInterestProcessed(String accountId, LocalDate periodEnd, String transactionRef) {
        Account account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new EntityNotFoundException("Account not found: " + accountId));
        if (periodEnd == null) throw new IllegalArgumentException("Interest period end is required");
        if (isBlank(transactionRef)) throw new IllegalArgumentException("Transaction reference is required");
        if (account.getInterestAccruedThrough() == null
                || account.getInterestAccruedThrough().isBefore(periodEnd)) {
            account.setInterestAccruedThrough(periodEnd);
            account.setNextInterestPayoutDate(YearMonth.from(periodEnd).plusMonths(1).atEndOfMonth());
            accountRepository.save(account);
            Map<String, Object> details = accountDetails(account);
            details.put("transactionRef", transactionRef);
            details.put("periodEnd", periodEnd);
            details.put("actorId", "SYSTEM");
            details.put("actorType", "SYSTEM");
            auditClient.success("accounts", "INTEREST_PERIOD_PROCESSED",
                    "No savings interest was credited for period ending " + periodEnd
                            + " because the calculated amount was zero", details);
        }
        return account;
    }

    @Transactional
    public Account create(Account account) {
        account.setAccountNumber(generateAccountNumber());
        applyProductRules(account, productsClient.getById(account.getProductId()));
        if (account.getAvailableBalance() == null) account.setAvailableBalance(BigDecimal.ZERO);
        if (account.getAvailableBalance().signum() != 0) {
            throw new IllegalArgumentException(
                    "Accounts must open at zero; post the opening amount as an opening-deposit transaction");
        }
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
        if (previousStatus != AccountStatus.CLOSED && account.getStatus() == AccountStatus.CLOSED) {
            validateClosure(existing);
        }
        // Account numbers are immutable. This also preserves legacy, non-numeric
        // numbers while every newly opened account uses the numeric format.
        existing.setCustomerId(account.getCustomerId());
        if (!existing.getProductId().equals(account.getProductId())) {
            existing.setProductId(account.getProductId());
            applyProductRules(existing, productsClient.getById(account.getProductId()));
        }
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
        ensureProductRules(debitAccount);
        ensureProductRules(creditAccount);

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
        validateTransferRules(request.effectivePurpose(), debitAccount, creditAccount,
                request.amount(), debitBalanceBefore, debitBalanceAfter, creditBalanceBefore, creditBalanceAfter);
        debitAccount.setAvailableBalance(debitBalanceAfter);
        creditAccount.setAvailableBalance(creditBalanceAfter);
        if (request.effectivePurpose() == TransferPurpose.FIXED_DEPOSIT_MATURITY
                || request.effectivePurpose() == TransferPurpose.FIXED_DEPOSIT_PREMATURE_CLOSURE) {
            AccountStatus previousStatus = debitAccount.getStatus();
            debitAccount.setStatus(AccountStatus.CLOSED);
            debitAccount.setClosedAt(java.time.LocalDateTime.now());
            accountStatusHistoryRepository.save(AccountStatusHistory.transition(debitAccount, previousStatus,
                    "transactions-service", request.effectivePurpose().name()));
        }
        accountRepository.saveAll(List.of(debitAccount, creditAccount));

        AccountTransferOperation operation = AccountTransferOperation.completed(
                request.transactionRef(), request.debitAccountId(), request.creditAccountId(),
                request.customerId(), request.effectivePurpose(), request.amount(), currencyCode,
                debitBalanceAfter, creditBalanceAfter);
        transferOperationRepository.save(operation);
        auditBalanceChange("BALANCE_DEBITED", debitAccount, operation.getTransactionRef(),
                operation.getOperationId(), operation.getAmount(), operation.getCurrencyCode(),
                debitBalanceBefore, debitBalanceAfter, transferDebitDescription(request.effectivePurpose()),
                request.effectivePurpose().name(), LocalDate.now(),
                request.effectivePurpose() == TransferPurpose.FIXED_DEPOSIT_MATURITY);
        auditBalanceChange("BALANCE_CREDITED", creditAccount, operation.getTransactionRef(),
                operation.getOperationId(), operation.getAmount(), operation.getCurrencyCode(),
                creditBalanceBefore, creditBalanceAfter, transferCreditDescription(request.effectivePurpose()),
                request.effectivePurpose().name(), LocalDate.now(),
                request.effectivePurpose() == TransferPurpose.FIXED_DEPOSIT_MATURITY);
        return response(operation);
    }

    private AccountTransferResponse replay(AccountTransferOperation operation,
                                            AccountTransferRequest request,
                                            String currencyCode) {
        if (!operation.matches(request.debitAccountId(), request.creditAccountId(), request.customerId(),
                request.effectivePurpose(),
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
        ensureProductRules(account);
        validatePostable(account, currencyCode, "Target");

        completed = balanceOperationRepository.findByTransactionRef(request.transactionRef()).orElse(null);
        if (completed != null) return replay(completed, accountId, request, currencyCode);

        BigDecimal balanceBefore = account.getAvailableBalance();
        BigDecimal balanceAfter;
        if (!isDebitAdjustment(request.adjustmentType())) {
            balanceAfter = account.getAvailableBalance().add(request.amount());
        } else {
            if (account.getAvailableBalance().compareTo(request.amount()) < 0) {
                throw new IllegalArgumentException("Insufficient available balance");
            }
            balanceAfter = account.getAvailableBalance().subtract(request.amount());
        }
        validateAdjustmentRules(account, request, balanceAfter);
        account.setAvailableBalance(balanceAfter);
        if (request.adjustmentType() == AccountAdjustmentRequest.AdjustmentType.INTEREST_CREDIT) {
            LocalDate effectiveDate = request.effectiveDate() == null ? LocalDate.now() : request.effectiveDate();
            account.setInterestAccruedThrough(effectiveDate);
            account.setNextInterestPayoutDate(YearMonth.from(effectiveDate).plusMonths(1).atEndOfMonth());
        }
        accountRepository.save(account);

        AccountBalanceOperation operation = AccountBalanceOperation.completed(
                request.transactionRef(), accountId, request.adjustmentType(), request.amount(),
                currencyCode, balanceAfter);
        balanceOperationRepository.save(operation);
        String action = !isDebitAdjustment(request.adjustmentType())
                ? "BALANCE_CREDITED" : "BALANCE_DEBITED";
        auditBalanceChange(action, account, operation.getTransactionRef(), operation.getOperationId(),
                operation.getAmount(), operation.getCurrencyCode(), balanceBefore, balanceAfter,
                adjustmentDescription(request), request.adjustmentType().name(), request.effectiveDate(),
                isSystemAdjustment(request.adjustmentType()));
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

    private void validateTransferRules(TransferPurpose purpose, Account debitAccount, Account creditAccount,
                                       BigDecimal amount, BigDecimal debitBalanceBefore,
                                       BigDecimal debitBalanceAfter, BigDecimal creditBalanceBefore,
                                       BigDecimal creditBalanceAfter) {
        boolean debitFixedDeposit = isType(debitAccount, "FD");
        boolean creditFixedDeposit = isType(creditAccount, "FD");
        switch (purpose) {
            case STANDARD -> {
                if (debitFixedDeposit || creditFixedDeposit) {
                    throw new IllegalArgumentException(
                            "Fixed deposits can only be funded or closed through the fixed-deposit workflow");
                }
                requireMinimumBalance(debitAccount, debitBalanceAfter);
            }
            case FIXED_DEPOSIT_FUNDING -> {
                if (debitFixedDeposit || !creditFixedDeposit) {
                    throw new IllegalArgumentException("Fixed-deposit funding must move money from a transactional account to an FD account");
                }
                if (creditBalanceBefore.signum() != 0) {
                    throw new IllegalArgumentException("Fixed deposit has already been funded");
                }
                requireMinimumBalance(debitAccount, debitBalanceAfter);
                requireOpeningAmount(creditAccount, amount);
            }
            case FIXED_DEPOSIT_MATURITY, FIXED_DEPOSIT_PREMATURE_CLOSURE -> {
                if (!debitFixedDeposit || creditFixedDeposit) {
                    throw new IllegalArgumentException("Fixed-deposit closure must pay from an FD account to a transactional account");
                }
                if (amount.compareTo(debitBalanceBefore) != 0 || debitBalanceAfter.signum() != 0) {
                    throw new IllegalArgumentException("The complete fixed-deposit principal must be withdrawn when it is closed");
                }
            }
        }
    }

    private void validateAdjustmentRules(Account account, AccountAdjustmentRequest request,
                                         BigDecimal balanceAfter) {
        if (isType(account, "FD")) {
            throw new IllegalArgumentException(
                    "Fixed-deposit balances can only change through the fixed-deposit workflow");
        }
        if (request.adjustmentType() == AccountAdjustmentRequest.AdjustmentType.WITHDRAWAL) {
            requireMinimumBalance(account, balanceAfter);
            return;
        }
        if (request.adjustmentType() == AccountAdjustmentRequest.AdjustmentType.OPENING_DEPOSIT) {
            if (account.getAvailableBalance().signum() != 0) {
                throw new IllegalArgumentException("Opening deposit can only be posted to a zero-balance account");
            }
            requireMinimumBalance(account, balanceAfter);
            return;
        }
        if (request.adjustmentType() == AccountAdjustmentRequest.AdjustmentType.MONTHLY_MAINTENANCE_FEE
                || request.adjustmentType() == AccountAdjustmentRequest.AdjustmentType.ANNUAL_MAINTENANCE_FEE) {
            if (!isType(account, "SAVINGS") && !isType(account, "CURRENT")) {
                throw new IllegalArgumentException("Annual maintenance fees apply only to savings and current accounts");
            }
            return;
        }
        if (request.adjustmentType() == AccountAdjustmentRequest.AdjustmentType.INTEREST_CREDIT) {
            if ((!isType(account, "SAVINGS") && !isType(account, "SALARY"))
                    || account.getAnnualInterestRate() == null
                    || account.getAnnualInterestRate().signum() <= 0) {
                throw new IllegalArgumentException("Interest can only be credited to an interest-bearing savings or salary account");
            }
        }
        if (request.adjustmentType()
                == AccountAdjustmentRequest.AdjustmentType.FIXED_DEPOSIT_INTEREST_CREDIT) {
            return;
        }
    }

    private boolean isDebitAdjustment(AccountAdjustmentRequest.AdjustmentType type) {
        return type == AccountAdjustmentRequest.AdjustmentType.WITHDRAWAL
                || type == AccountAdjustmentRequest.AdjustmentType.MONTHLY_MAINTENANCE_FEE
                || type == AccountAdjustmentRequest.AdjustmentType.ANNUAL_MAINTENANCE_FEE;
    }

    private void requireMinimumBalance(Account account, BigDecimal balanceAfter) {
        BigDecimal minimum = account.getMinimumBalance() == null ? BigDecimal.ZERO : account.getMinimumBalance();
        if (balanceAfter.compareTo(minimum) < 0) {
            throw new IllegalArgumentException("Withdrawal would reduce account " + account.getAccountNumber()
                    + " below its minimum balance of " + minimum.toPlainString() + " " + account.getCurrencyCode());
        }
    }

    private void requireOpeningAmount(Account fixedDeposit, BigDecimal amount) {
        BigDecimal minimum = fixedDeposit.getMinimumBalance() == null ? BigDecimal.ZERO : fixedDeposit.getMinimumBalance();
        if (amount.compareTo(minimum) < 0) {
            throw new IllegalArgumentException("Fixed-deposit principal must be at least " + minimum.toPlainString()
                    + " " + fixedDeposit.getCurrencyCode());
        }
    }

    private void ensureProductRules(Account account) {
        if (account.getProductTypeCode() != null) return;
        applyProductRules(account, productsClient.getById(account.getProductId()));
    }

    private void applyProductRules(Account account, ProductRulesResponse product) {
        if (!"ACTIVE".equalsIgnoreCase(product.status())) {
            throw new IllegalArgumentException("Account cannot use an inactive or retired product");
        }
        if (!account.getCurrencyCode().equalsIgnoreCase(product.currency())) {
            throw new IllegalArgumentException("Account currency must match product currency " + product.currency());
        }
        String type = product.productTypeCode() == null ? "" : product.productTypeCode().toUpperCase();
        if (!List.of("SAVINGS", "SALARY", "CURRENT", "FD").contains(type)) {
            throw new IllegalArgumentException("Unsupported deposit account product type: " + type);
        }
        account.setProductTypeCode(type);
        account.setMinimumBalance(product.minimumBalance() == null ? BigDecimal.ZERO : product.minimumBalance());
        account.setMaximumBalance(null);
        account.setAnnualInterestRate("CURRENT".equals(type) ? BigDecimal.ZERO : product.annualInterestRate());
        ProductRulesResponse.Term term = product.term();
        account.setTenureMonths(term == null ? null : term.tenureMonths());
        account.setLockInPeriodMonths("FD".equals(type)
                ? Integer.valueOf(0) : term == null ? null : term.lockInPeriod());
        account.setMaturityInstruction(term == null ? null : term.maturityInstruction());
        account.setPrematureWithdrawalAllowed("FD".equals(type)
                || term != null && Boolean.TRUE.equals(term.prematureWithdrawalAllowed()));
        if (("SAVINGS".equals(type) || "SALARY".equals(type))
                && account.getAnnualInterestRate().signum() > 0
                && account.getNextInterestPayoutDate() == null) {
            LocalDate today = LocalDate.now();
            account.setInterestAccruedThrough(today.minusDays(1));
            account.setNextInterestPayoutDate(YearMonth.from(today).atEndOfMonth());
        }
    }

    private boolean isType(Account account, String productType) {
        return productType.equalsIgnoreCase(account.getProductTypeCode());
    }

    private void validateClosure(Account account) {
        if (isType(account, "FD")) {
            throw new IllegalArgumentException(
                    "Use the fixed-deposit withdrawal or maturity workflow to close an FD account");
        }
        if (!fixedDepositsClient.activeForAccount(account.getAccountId()).isEmpty()) {
            throw new IllegalArgumentException(
                    "This account has an active fixed deposit. Keep it open or withdraw the fixed deposit first");
        }
        if (account.getAvailableBalance().signum() != 0) {
            throw new IllegalArgumentException(
                    "Transfer or withdraw the complete account balance before closing the account");
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
        if (isBlank(account.getProductTypeCode())) throw new IllegalArgumentException("Product type is required");
        if (isBlank(account.getCurrencyCode())) throw new IllegalArgumentException("Currency code is required");
        if (account.getAvailableBalance() == null || account.getAvailableBalance().signum() < 0) {
            throw new IllegalArgumentException("Available balance cannot be negative");
        }
        if (isType(account, "FD")) {
            if (account.getAvailableBalance().signum() != 0) {
                throw new IllegalArgumentException("A fixed-deposit account must open at zero and be funded from another bank account");
            }
            if (account.getTenureMonths() == null || account.getTenureMonths() <= 0) {
                throw new IllegalArgumentException("Fixed-deposit product must define a positive tenure");
            }
            if (!"CREDIT_TO_ACCOUNT".equalsIgnoreCase(account.getMaturityInstruction())) {
                throw new IllegalArgumentException("Only CREDIT_TO_ACCOUNT maturity instruction is currently supported");
            }
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
                                    BigDecimal balanceBefore, BigDecimal balanceAfter, String reason,
                                    String postingType, LocalDate effectiveDate, boolean systemGenerated) {
        Map<String, Object> details = accountDetails(account);
        details.put("transactionRef", transactionRef);
        details.put("operationId", operationId);
        details.put("amount", amount);
        details.put("currencyCode", currencyCode);
        details.put("balanceBefore", balanceBefore);
        details.put("balanceAfter", balanceAfter);
        details.put("reason", reason);
        details.put("postingType", postingType);
        details.put("effectiveDate", effectiveDate);
        if (systemGenerated) {
            details.put("actorId", "SYSTEM");
            details.put("actorType", "SYSTEM");
        }
        putChanges(details, Map.of("availableBalance", balanceBefore),
                Map.of("availableBalance", balanceAfter));
        auditClient.success("accounts", action, reason, details);
    }

    private String adjustmentDescription(AccountAdjustmentRequest request) {
        LocalDate date = request.effectiveDate() == null ? LocalDate.now() : request.effectiveDate();
        return switch (request.adjustmentType()) {
            case INTEREST_CREDIT -> "Savings interest credited for period ending " + date;
            case FIXED_DEPOSIT_INTEREST_CREDIT ->
                    "Fixed-deposit interest credited for period ending " + date;
            case MONTHLY_MAINTENANCE_FEE ->
                    "Monthly maintenance fee charged for " + YearMonth.from(date);
            case ANNUAL_MAINTENANCE_FEE ->
                    "Annual maintenance fee charged for the " + annualFeeYear(request.transactionRef(), date)
                            + " account anniversary";
            case OPENING_DEPOSIT -> "Opening deposit credited";
            case DEPOSIT -> "Deposit credited";
            case WITHDRAWAL -> "Withdrawal debited";
        };
    }

    private String transferDebitDescription(TransferPurpose purpose) {
        return switch (purpose) {
            case FIXED_DEPOSIT_FUNDING -> "Fixed-deposit principal debited from the funding account";
            case FIXED_DEPOSIT_MATURITY -> "Fixed-deposit principal debited from the matured deposit account";
            case FIXED_DEPOSIT_PREMATURE_CLOSURE ->
                    "Fixed-deposit principal debited after premature closure";
            default -> "Transfer amount debited";
        };
    }

    private String transferCreditDescription(TransferPurpose purpose) {
        return switch (purpose) {
            case FIXED_DEPOSIT_FUNDING -> "Fixed-deposit principal credited to the deposit account";
            case FIXED_DEPOSIT_MATURITY ->
                    "Fixed-deposit principal credited to the original funding account on maturity";
            case FIXED_DEPOSIT_PREMATURE_CLOSURE ->
                    "Fixed-deposit principal credited to the original funding account after premature closure";
            default -> "Transfer amount credited";
        };
    }

    private int annualFeeYear(String transactionRef, LocalDate fallback) {
        if (transactionRef != null && transactionRef.matches("AF\\d{4}-.+")) {
            return Integer.parseInt(transactionRef.substring(2, 6));
        }
        return fallback.getYear();
    }

    private boolean isSystemAdjustment(AccountAdjustmentRequest.AdjustmentType type) {
        return type == AccountAdjustmentRequest.AdjustmentType.INTEREST_CREDIT
                || type == AccountAdjustmentRequest.AdjustmentType.FIXED_DEPOSIT_INTEREST_CREDIT
                || type == AccountAdjustmentRequest.AdjustmentType.MONTHLY_MAINTENANCE_FEE
                || type == AccountAdjustmentRequest.AdjustmentType.ANNUAL_MAINTENANCE_FEE;
    }

    private Map<String, Object> accountValues(Account account) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("accountNumber", account.getAccountNumber());
        values.put("customerId", account.getCustomerId());
        values.put("productId", account.getProductId());
        values.put("productTypeCode", account.getProductTypeCode());
        values.put("minimumBalance", account.getMinimumBalance());
        values.put("annualInterestRate", account.getAnnualInterestRate());
        values.put("tenureMonths", account.getTenureMonths());
        values.put("lockInPeriodMonths", account.getLockInPeriodMonths());
        values.put("maturityInstruction", account.getMaturityInstruction());
        values.put("prematureWithdrawalAllowed", account.getPrematureWithdrawalAllowed());
        values.put("interestAccruedThrough", account.getInterestAccruedThrough());
        values.put("nextInterestPayoutDate", account.getNextInterestPayoutDate());
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
