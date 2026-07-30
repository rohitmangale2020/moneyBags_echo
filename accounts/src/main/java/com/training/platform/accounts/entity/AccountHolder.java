package com.training.platform.accounts.entity;

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
@Table(name = "account_holder", uniqueConstraints = @UniqueConstraint(name = "uq_account_customer", columnNames = {"account_id", "customer_id"}), indexes = @Index(name = "idx_holder_customer", columnList = "customer_id"))
public class AccountHolder {
    @Id @Column(name = "account_holder_id", length = 36) private String accountHolderId;
    @ManyToOne(optional = false) @JoinColumn(name = "account_id", nullable = false) private Account account;
    @Column(name = "customer_id", nullable = false, length = 36) private String customerId;
    @Column(name = "holder_role", nullable = false, length = 20) private String holderRole;
    @Column(name = "operating_rule", nullable = false, length = 30) private String operatingRule;
    @Column(name = "signing_authority", nullable = false, length = 1) private String signingAuthority = "Y";
    @Column(name = "holder_status", nullable = false, length = 20) private String holderStatus = "ACTIVE";
    @Column(name = "added_at", nullable = false) private LocalDateTime addedAt;
    @Column(name = "removed_at") private LocalDateTime removedAt;

    protected AccountHolder() { }
    @jakarta.persistence.PrePersist void beforeInsert() { if (accountHolderId == null) accountHolderId = UUID.randomUUID().toString(); if (addedAt == null) addedAt = LocalDateTime.now(); }
}
