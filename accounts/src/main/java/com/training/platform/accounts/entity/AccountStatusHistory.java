package com.training.platform.accounts.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "account_status_history", indexes = @Index(name = "idx_status_history_account", columnList = "account_id"))
public class AccountStatusHistory {
    @Id @Column(name = "status_history_id", length = 36) private String statusHistoryId;
    @ManyToOne(optional = false) @JoinColumn(name = "account_id", nullable = false) private Account account;
    @Column(name = "previous_status", length = 20) private String previousStatus;
    @Column(name = "new_status", nullable = false, length = 20) private String newStatus;
    @Column(name = "changed_by_user_id", length = 36) private String changedByUserId;
    @Column(length = 500) private String reason;
    @Column(name = "changed_at", nullable = false) private LocalDateTime changedAt;

    protected AccountStatusHistory() { }
    @jakarta.persistence.PrePersist void beforeInsert() { if (statusHistoryId == null) statusHistoryId = UUID.randomUUID().toString(); if (changedAt == null) changedAt = LocalDateTime.now(); }
}
