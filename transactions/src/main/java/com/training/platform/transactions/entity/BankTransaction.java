package com.training.platform.transactions.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "bank_transaction", uniqueConstraints = @UniqueConstraint(name = "uk_transaction_ref", columnNames = "transaction_ref"), indexes = {
        @Index(name = "idx_transaction_debit_account", columnList = "debit_account_id, initiated_at"),
        @Index(name = "idx_transaction_credit_account", columnList = "credit_account_id, initiated_at"),
        @Index(name = "idx_transaction_status", columnList = "transaction_status, initiated_at")
})
public class BankTransaction {
    @Id @Column(name = "transaction_id", length = 36) private String transactionId;
    @Column(name = "transaction_ref", nullable = false, length = 40) private String transactionRef;
    @Column(name = "transaction_type", nullable = false, length = 30) private String transactionType;
    @Column(name = "transaction_status", nullable = false, length = 20) private String transactionStatus;
    @Column(name = "transaction_channel", nullable = false, length = 30) private String transactionChannel;
    @Column(name = "debit_account_id", length = 36) private String debitAccountId;
    @Column(name = "credit_account_id", length = 36) private String creditAccountId;
    @Column(name = "external_beneficiary", length = 200) private String externalBeneficiary;
    @Column(nullable = false, precision = 19, scale = 4) private BigDecimal amount;
    @Column(name = "currency_code", nullable = false, length = 3) private String currencyCode;
    @Column(name = "fee_amount", nullable = false, precision = 19, scale = 4) private BigDecimal feeAmount = BigDecimal.ZERO;
    @Column(name = "initiated_by_customer_id", length = 36) private String initiatedByCustomerId;
    @Column(name = "initiated_by_user_id", length = 36) private String initiatedByUserId;
    @Column(name = "initiated_at", nullable = false) private LocalDateTime initiatedAt;
    @Column(name = "completed_at") private LocalDateTime completedAt;
    @Column(name = "failure_code", length = 50) private String failureCode;
    @Column(name = "failure_reason", length = 500) private String failureReason;
    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, orphanRemoval = true) private List<TransactionApproval> approvals = new ArrayList<>();
    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, orphanRemoval = true) private List<TransactionChannelDetail> channelDetails = new ArrayList<>();

    protected BankTransaction() { }
    @jakarta.persistence.PrePersist void beforeInsert() { if (transactionId == null) transactionId = UUID.randomUUID().toString(); if (initiatedAt == null) initiatedAt = LocalDateTime.now(); }
}
