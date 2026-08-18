package com.training.platform.transactions.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "transaction_approval",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_transaction_account_holder",
                columnNames = {"transaction_id", "account_holder_account_id", "account_holder_customer_id"}),
        indexes = @Index(name = "idx_approval_transaction", columnList = "transaction_id"))
/** Captures an account holder's approval decision for a transaction. */
public class TransactionApproval {
    @Id @Column(name = "approval_id", length = 36) private String approvalId;
    @ManyToOne(optional = false) @JoinColumn(name = "transaction_id", nullable = false) private BankTransaction transaction;
    @Column(name = "account_holder_account_id", nullable = false, length = 36) private String accountHolderAccountId;
    @Column(name = "account_holder_customer_id", nullable = false, length = 36) private String accountHolderCustomerId;
    @Enumerated(EnumType.STRING) @Column(name = "approval_status", nullable = false, length = 20) private ApprovalStatus approvalStatus;
    @Column(name = "approval_note", length = 500) private String approvalNote;
    @Column(name = "approved_at") private LocalDateTime approvedAt;

    protected TransactionApproval() { }
    @jakarta.persistence.PrePersist void beforeInsert() { if (approvalId == null) approvalId = UUID.randomUUID().toString(); }

    /** Records an internal staff review while retaining the legacy customer-approval table. */
    public static TransactionApproval riskDecision(BankTransaction transaction, String approverId,
                                                   ApprovalStatus status, String note) {
        TransactionApproval approval = new TransactionApproval();
        approval.transaction = transaction;
        approval.accountHolderAccountId = "RISK_ADMIN";
        approval.accountHolderCustomerId = approverId;
        approval.approvalStatus = status;
        approval.approvalNote = note;
        approval.approvedAt = LocalDateTime.now();
        return approval;
    }
}
