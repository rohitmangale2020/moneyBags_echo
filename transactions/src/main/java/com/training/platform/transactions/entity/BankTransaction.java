package com.training.platform.transactions.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "bank_transaction", uniqueConstraints = @UniqueConstraint(name = "uk_transaction_ref", columnNames = "transaction_ref"), indexes = {
        @Index(name = "idx_transaction_debit_account", columnList = "debit_account_id, initiated_at"),
        @Index(name = "idx_transaction_credit_account", columnList = "credit_account_id, initiated_at"),
        @Index(name = "idx_transaction_status", columnList = "transaction_status, initiated_at")
})
/** Represents a monetary transfer, including its accounts, amount, status. */
public class BankTransaction {
    @Id @Column(name = "transaction_id", length = 36) private String transactionId;
    @Column(name = "transaction_ref", nullable = false, length = 40) private String transactionRef;
    @Enumerated(EnumType.STRING) @Column(name = "transaction_type", nullable = false, length = 40) private TransactionType transactionType;
    @Enumerated(EnumType.STRING) @Column(name = "transaction_status", nullable = false, length = 30) private TransactionStatus transactionStatus;
    @Column(name = "debit_account_id", length = 36) private String debitAccountId;
    @Column(name = "credit_account_id", length = 36) private String creditAccountId;
    @Column(name = "external_beneficiary", length = 200) private String externalBeneficiary;
    @Column(length = 500) private String description;
    @Column(nullable = false, precision = 19, scale = 4) private BigDecimal amount;
    @Column(name = "currency_code", nullable = false, length = 3) private String currencyCode;
    @Column(name = "fee_amount", nullable = false, precision = 19, scale = 4) private BigDecimal feeAmount = BigDecimal.ZERO;
    @Column(name = "initiated_by_customer_id", length = 36) private String initiatedByCustomerId;
    @Column(name = "initiated_by_user_id", length = 36) private String initiatedByUserId;
    @Column(name = "initiated_at", nullable = false) private LocalDateTime initiatedAt;
    @Column(name = "completed_at") private LocalDateTime completedAt;
    @Column(name = "interest_period_end") private LocalDate interestPeriodEnd;
    @Column(name = "failure_code", length = 50) private String failureCode;
    @Column(name = "failure_reason", length = 500) private String failureReason;
    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, orphanRemoval = true) private List<TransactionApproval> approvals = new ArrayList<>();
    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, orphanRemoval = true) private List<AccountStatement> statements = new ArrayList<>();

    public BankTransaction() { }
    @jakarta.persistence.PrePersist void beforeInsert() { if (transactionId == null) transactionId = UUID.randomUUID().toString(); if (initiatedAt == null) initiatedAt = LocalDateTime.now(); }

    public TransactionType getTransactionType() { return transactionType; }
    public TransactionStatus getTransactionStatus() { return transactionStatus; }
    public String getTransactionId() { return transactionId; }
    public String getTransactionRef() { return transactionRef; }
    public String getDebitAccountId() { return debitAccountId; }
    public String getCreditAccountId() { return creditAccountId; }
    public String getExternalBeneficiary() { return externalBeneficiary; }
    public String getDescription() { return description; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrencyCode() { return currencyCode; }
    public BigDecimal getFeeAmount() { return feeAmount; }
    public String getInitiatedByCustomerId() { return initiatedByCustomerId; }
    public String getInitiatedByUserId() { return initiatedByUserId; }
    public LocalDateTime getInitiatedAt() { return initiatedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public LocalDate getInterestPeriodEnd() { return interestPeriodEnd; }
    public String getFailureCode() { return failureCode; }
    public String getFailureReason() { return failureReason; }

    public void setTransactionRef(String transactionRef) { this.transactionRef = transactionRef; }
    public void setTransactionType(TransactionType transactionType) { this.transactionType = transactionType; }
    public void setTransactionStatus(TransactionStatus transactionStatus) { this.transactionStatus = transactionStatus; }
    public void setDebitAccountId(String debitAccountId) { this.debitAccountId = debitAccountId; }
    public void setCreditAccountId(String creditAccountId) { this.creditAccountId = creditAccountId; }
    public void setExternalBeneficiary(String externalBeneficiary) { this.externalBeneficiary = externalBeneficiary; }
    public void setDescription(String description) { this.description = description; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }
    public void setFeeAmount(BigDecimal feeAmount) { this.feeAmount = feeAmount; }
    public void setInitiatedByCustomerId(String initiatedByCustomerId) { this.initiatedByCustomerId = initiatedByCustomerId; }
    public void setInitiatedByUserId(String initiatedByUserId) { this.initiatedByUserId = initiatedByUserId; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public void setInterestPeriodEnd(LocalDate interestPeriodEnd) { this.interestPeriodEnd = interestPeriodEnd; }
    public void setFailureCode(String failureCode) { this.failureCode = failureCode; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
}
