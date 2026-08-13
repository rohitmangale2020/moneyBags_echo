package com.training.platform.accounts.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Idempotency record for a posted transfer. A transaction reference can move
 * balances only once, even when the transactions service retries the request.
 */
@Entity
@Table(name = "account_transfer_operation",
        uniqueConstraints = @UniqueConstraint(name = "uk_account_transfer_ref", columnNames = "transaction_ref"),
        indexes = {
                @Index(name = "idx_account_transfer_debit", columnList = "debit_account_id"),
                @Index(name = "idx_account_transfer_credit", columnList = "credit_account_id")
        })
public class AccountTransferOperation {
    @Id
    @Column(name = "operation_id", length = 36)
    private String operationId;

    @Column(name = "transaction_ref", nullable = false, length = 40)
    private String transactionRef;

    @Column(name = "debit_account_id", nullable = false, length = 36)
    private String debitAccountId;

    @Column(name = "credit_account_id", nullable = false, length = 36)
    private String creditAccountId;

    @Column(name = "customer_id", length = 36)
    private String customerId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "debit_balance_after", nullable = false, precision = 19, scale = 4)
    private BigDecimal debitBalanceAfter;

    @Column(name = "credit_balance_after", nullable = false, precision = 19, scale = 4)
    private BigDecimal creditBalanceAfter;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;

    protected AccountTransferOperation() { }

    public static AccountTransferOperation completed(
            String transactionRef,
            String debitAccountId,
            String creditAccountId,
            String customerId,
            BigDecimal amount,
            String currencyCode,
            BigDecimal debitBalanceAfter,
            BigDecimal creditBalanceAfter) {
        AccountTransferOperation operation = new AccountTransferOperation();
        operation.operationId = UUID.randomUUID().toString();
        operation.transactionRef = transactionRef;
        operation.debitAccountId = debitAccountId;
        operation.creditAccountId = creditAccountId;
        operation.customerId = customerId;
        operation.amount = amount;
        operation.currencyCode = currencyCode;
        operation.debitBalanceAfter = debitBalanceAfter;
        operation.creditBalanceAfter = creditBalanceAfter;
        operation.processedAt = LocalDateTime.now();
        return operation;
    }

    public boolean matches(String debitId, String creditId, String requestedCustomerId,
                           BigDecimal requestedAmount, String requestedCurrency) {
        return debitAccountId.equals(debitId)
                && creditAccountId.equals(creditId)
                && java.util.Objects.equals(customerId, requestedCustomerId)
                && amount.compareTo(requestedAmount) == 0
                && currencyCode.equalsIgnoreCase(requestedCurrency);
    }

    public String getTransactionRef() { return transactionRef; }
    public String getDebitAccountId() { return debitAccountId; }
    public String getCreditAccountId() { return creditAccountId; }
    public BigDecimal getDebitBalanceAfter() { return debitBalanceAfter; }
    public BigDecimal getCreditBalanceAfter() { return creditBalanceAfter; }
    public LocalDateTime getProcessedAt() { return processedAt; }
}
