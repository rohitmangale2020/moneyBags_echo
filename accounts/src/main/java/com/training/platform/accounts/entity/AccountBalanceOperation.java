package com.training.platform.accounts.entity;

import com.training.platform.accounts.dto.AccountAdjustmentRequest.AdjustmentType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** Idempotency record for a deposit or withdrawal posted to one account. */
@Entity
@Table(name = "account_balance_operation",
        uniqueConstraints = @UniqueConstraint(name = "uk_account_balance_ref", columnNames = "transaction_ref"),
        indexes = @Index(name = "idx_account_balance_account", columnList = "account_id"))
public class AccountBalanceOperation {
    @Id
    @Column(name = "operation_id", length = 36)
    private String operationId;

    @Column(name = "transaction_ref", nullable = false, length = 40)
    private String transactionRef;

    @Column(name = "account_id", nullable = false, length = 36)
    private String accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "adjustment_type", nullable = false, length = 20)
    private AdjustmentType adjustmentType;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "balance_after", nullable = false, precision = 19, scale = 4)
    private BigDecimal balanceAfter;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;

    protected AccountBalanceOperation() { }

    public static AccountBalanceOperation completed(String transactionRef, String accountId,
                                                     AdjustmentType adjustmentType, BigDecimal amount,
                                                     String currencyCode, BigDecimal balanceAfter) {
        AccountBalanceOperation operation = new AccountBalanceOperation();
        operation.operationId = UUID.randomUUID().toString();
        operation.transactionRef = transactionRef;
        operation.accountId = accountId;
        operation.adjustmentType = adjustmentType;
        operation.amount = amount;
        operation.currencyCode = currencyCode;
        operation.balanceAfter = balanceAfter;
        operation.processedAt = LocalDateTime.now();
        return operation;
    }

    public boolean matches(String requestedAccountId, AdjustmentType requestedType,
                           BigDecimal requestedAmount, String requestedCurrency) {
        return accountId.equals(requestedAccountId)
                && adjustmentType == requestedType
                && amount.compareTo(requestedAmount) == 0
                && currencyCode.equalsIgnoreCase(requestedCurrency);
    }

    public String getTransactionRef() { return transactionRef; }
    public String getAccountId() { return accountId; }
    public AdjustmentType getAdjustmentType() { return adjustmentType; }
    public BigDecimal getBalanceAfter() { return balanceAfter; }
    public LocalDateTime getProcessedAt() { return processedAt; }
}
