package com.training.platform.transactions.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "transaction_approval", uniqueConstraints = @UniqueConstraint(name = "uq_transaction_approver", columnNames = {"transaction_id", "approver_customer_id"}), indexes = @Index(name = "idx_approval_transaction", columnList = "transaction_id"))
public class TransactionApproval {
    @Id @Column(name = "approval_id", length = 36) private String approvalId;
    @ManyToOne(optional = false) @JoinColumn(name = "transaction_id", nullable = false) private BankTransaction transaction;
    @Column(name = "account_holder_id", nullable = false, length = 36) private String accountHolderId;
    @Column(name = "approver_customer_id", nullable = false, length = 36) private String approverCustomerId;
    @Column(name = "approval_status", nullable = false, length = 20) private String approvalStatus;
    @Column(name = "approval_note", length = 500) private String approvalNote;
    @Column(name = "approved_at") private LocalDateTime approvedAt;

    protected TransactionApproval() { }
    @jakarta.persistence.PrePersist void beforeInsert() { if (approvalId == null) approvalId = UUID.randomUUID().toString(); }
}
