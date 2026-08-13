package com.training.platform.transactions.service;

import com.training.platform.transactions.entity.AccountStatement;
import com.training.platform.transactions.entity.StatementEntryType;
import com.training.platform.transactions.entity.TransactionChannel;
import com.training.platform.transactions.entity.TransactionType;
import com.training.platform.transactions.repository.BankTransactionRepository;
import com.training.platform.transactions.repository.AccountStatementRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
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

    public List<AccountStatement> search(String accountId, LocalDate fromDate, LocalDate toDate,
                                         StatementEntryType entryType, TransactionChannel channel) {
        if (isBlank(accountId)) throw new IllegalArgumentException("Account ID is required");
        YearMonth currentMonth = YearMonth.now();
        LocalDate effectiveFrom = fromDate == null ? currentMonth.atDay(1) : fromDate;
        LocalDate effectiveTo = toDate == null ? currentMonth.atEndOfMonth() : toDate;
        if (effectiveFrom.isAfter(effectiveTo)) {
            throw new IllegalArgumentException("From date cannot be after to date");
        }

        Specification<AccountStatement> specification = (root, query, builder) ->
                builder.equal(root.get("accountId"), accountId);
        specification = specification.and((root, query, builder) ->
                builder.greaterThanOrEqualTo(root.get("postedAt"), effectiveFrom.atStartOfDay()));
        specification = specification.and((root, query, builder) ->
                builder.lessThan(root.get("postedAt"), effectiveTo.plusDays(1).atStartOfDay()));
        if (entryType != null) {
            specification = specification.and((root, query, builder) ->
                    builder.equal(root.get("entryType"), entryType));
        }
        if (channel != null) specification = specification.and(channelSpecification(channel));
        return statementRepository.findAll(specification, Sort.by(Sort.Direction.DESC, "postedAt"));
    }

    private Specification<AccountStatement> channelSpecification(TransactionChannel channel) {
        return (root, query, builder) -> {
            var transaction = root.join("transaction");
            return switch (channel) {
                case DEPOSIT -> builder.equal(transaction.get("transactionType"), TransactionType.DEPOSIT);
                case WITHDRAWAL -> builder.equal(transaction.get("transactionType"), TransactionType.WITHDRAWAL);
                case INTERNAL_TRANSFER -> builder.and(
                        builder.equal(transaction.get("transactionType"), TransactionType.TRANSFER),
                        builder.or(builder.isNull(transaction.get("initiatedByCustomerId")),
                                builder.equal(transaction.get("initiatedByCustomerId"), "")));
                case SELF_TRANSFER -> builder.and(
                        builder.equal(transaction.get("transactionType"), TransactionType.TRANSFER),
                        builder.isNotNull(transaction.get("initiatedByCustomerId")),
                        builder.notEqual(transaction.get("initiatedByCustomerId"), ""));
            };
        };
    }

    public List<AccountStatement> getMonthlyStatement(String accountId, int year, int month) {
        if (isBlank(accountId)) throw new IllegalArgumentException("Account ID is required");

        try {
            YearMonth requestedMonth = YearMonth.of(year, month);
            LocalDateTime start = requestedMonth.atDay(1).atStartOfDay();
            LocalDateTime end = requestedMonth.plusMonths(1).atDay(1).atStartOfDay();
            return statementRepository
                    .findByAccountIdAndPostedAtGreaterThanEqualAndPostedAtLessThanOrderByPostedAtAsc(
                            accountId, start, end);
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException("Invalid statement year or month", exception);
        }
    }

    @Transactional
    public AccountStatement record(String transactionId, AccountStatement statement) {
        statement.setTransaction(transactionRepository.findById(transactionId)
                .orElseThrow(() -> new EntityNotFoundException("Transaction not found: " + transactionId)));
        validate(statement);
        statement.setWithdrawalAmount(statement.getEntryType() == StatementEntryType.DEBIT
                ? statement.getAmount() : null);
        statement.setDepositAmount(statement.getEntryType() == StatementEntryType.CREDIT
                ? statement.getAmount() : null);
        statement.setClosingBalance(statement.getBalanceAfter());
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
