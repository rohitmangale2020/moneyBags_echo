package com.training.platform.audit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/** Transaction, approval, statement and outbox events produced by the transactions service. */
@Entity
@Table(name = "transaction_audit_log", indexes = {
        @Index(name = "idx_txn_audit_corr", columnList = "correlation_id"),
        @Index(name = "idx_txn_audit_txn", columnList = "transaction_id, created_at"),
        @Index(name = "idx_txn_audit_ref", columnList = "transaction_ref"),
        @Index(name = "idx_txn_audit_action", columnList = "action, created_at")
})
public class TransactionAuditLog extends BaseAuditLog {
    @Column(name = "transaction_id", updatable = false, length = 36)
    private String transactionId;

    @Column(name = "transaction_ref", updatable = false, length = 40)
    private String transactionRef;

    @Column(name = "debit_account_id", updatable = false, length = 36)
    private String debitAccountId;

    @Column(name = "credit_account_id", updatable = false, length = 36)
    private String creditAccountId;

    @Column(name = "previous_status", updatable = false, length = 30)
    private String previousStatus;

    @Column(name = "new_status", updatable = false, length = 30)
    private String newStatus;

    @Column(updatable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency_code", updatable = false, length = 3)
    private String currencyCode;

    @Column(name = "related_entity_type", updatable = false, length = 30)
    private String relatedEntityType;

    @Column(name = "related_entity_id", updatable = false, length = 36)
    private String relatedEntityId;

    @Column(name = "failure_reason", updatable = false, length = 500)
    private String failureReason;

    public TransactionAuditLog() { }

    public String getTransactionId() { return transactionId; }
    public String getTransactionRef() { return transactionRef; }
    public String getDebitAccountId() { return debitAccountId; }
    public String getCreditAccountId() { return creditAccountId; }
    public String getPreviousStatus() { return previousStatus; }
    public String getNewStatus() { return newStatus; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrencyCode() { return currencyCode; }
    public String getRelatedEntityType() { return relatedEntityType; }
    public String getRelatedEntityId() { return relatedEntityId; }
    public String getFailureReason() { return failureReason; }

    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
    public void setTransactionRef(String transactionRef) { this.transactionRef = transactionRef; }
    public void setDebitAccountId(String debitAccountId) { this.debitAccountId = debitAccountId; }
    public void setCreditAccountId(String creditAccountId) { this.creditAccountId = creditAccountId; }
    public void setPreviousStatus(String previousStatus) { this.previousStatus = previousStatus; }
    public void setNewStatus(String newStatus) { this.newStatus = newStatus; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }
    public void setRelatedEntityType(String relatedEntityType) { this.relatedEntityType = relatedEntityType; }
    public void setRelatedEntityId(String relatedEntityId) { this.relatedEntityId = relatedEntityId; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
}
