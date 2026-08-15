package com.training.platform.audit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/** Account lifecycle, holder, balance and transfer events produced by the accounts service. */
@Entity
@Table(name = "account_audit_log", indexes = {
        @Index(name = "idx_acct_audit_corr", columnList = "correlation_id"),
        @Index(name = "idx_acct_audit_account", columnList = "account_id, created_at"),
        @Index(name = "idx_acct_audit_txn", columnList = "transaction_id"),
        @Index(name = "idx_acct_audit_ref", columnList = "transaction_ref"),
        @Index(name = "idx_acct_audit_action", columnList = "action, created_at")
})
public class AccountAuditLog extends BaseAuditLog {
    @Column(name = "account_id", updatable = false, length = 36)
    private String accountId;

    @Column(name = "customer_id", updatable = false, length = 36)
    private String customerId;

    @Column(name = "transaction_id", updatable = false, length = 36)
    private String transactionId;

    @Column(name = "transaction_ref", updatable = false, length = 40)
    private String transactionRef;

    @Column(name = "operation_id", updatable = false, length = 36)
    private String operationId;

    @Column(name = "previous_status", updatable = false, length = 20)
    private String previousStatus;

    @Column(name = "new_status", updatable = false, length = 20)
    private String newStatus;

    @Column(updatable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency_code", updatable = false, length = 3)
    private String currencyCode;

    @Column(name = "balance_before", updatable = false, precision = 19, scale = 4)
    private BigDecimal balanceBefore;

    @Column(name = "balance_after", updatable = false, precision = 19, scale = 4)
    private BigDecimal balanceAfter;

    @Column(updatable = false, length = 500)
    private String reason;

    public AccountAuditLog() { }

    public String getAccountId() { return accountId; }
    public String getCustomerId() { return customerId; }
    public String getTransactionId() { return transactionId; }
    public String getTransactionRef() { return transactionRef; }
    public String getOperationId() { return operationId; }
    public String getPreviousStatus() { return previousStatus; }
    public String getNewStatus() { return newStatus; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrencyCode() { return currencyCode; }
    public BigDecimal getBalanceBefore() { return balanceBefore; }
    public BigDecimal getBalanceAfter() { return balanceAfter; }
    public String getReason() { return reason; }

    public void setAccountId(String accountId) { this.accountId = accountId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
    public void setTransactionRef(String transactionRef) { this.transactionRef = transactionRef; }
    public void setOperationId(String operationId) { this.operationId = operationId; }
    public void setPreviousStatus(String previousStatus) { this.previousStatus = previousStatus; }
    public void setNewStatus(String newStatus) { this.newStatus = newStatus; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }
    public void setBalanceBefore(BigDecimal balanceBefore) { this.balanceBefore = balanceBefore; }
    public void setBalanceAfter(BigDecimal balanceAfter) { this.balanceAfter = balanceAfter; }
    public void setReason(String reason) { this.reason = reason; }
}
