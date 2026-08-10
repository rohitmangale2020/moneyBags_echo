package com.training.platform.accounts.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "account_holder", indexes = @Index(name = "idx_holder_customer", columnList = "customer_id"))
/** Represents a customer's ownership role and operating authority for an account. */
public class AccountHolder {
    @EmbeddedId private AccountHolderId id;
    @ManyToOne(optional = false) @JoinColumn(name = "account_id", nullable = false, insertable = false, updatable = false) private Account account;
    @Enumerated(EnumType.STRING) @Column(name = "holder_role", nullable = false, length = 30) private HolderRole holderRole;
    @Enumerated(EnumType.STRING) @Column(name = "operating_rule", nullable = false, length = 30) private OperatingRule operatingRule;
    @Enumerated(EnumType.STRING) @Column(name = "signing_authority", nullable = false, length = 20) private SigningAuthority signingAuthority = SigningAuthority.AUTHORIZED;
    @Enumerated(EnumType.STRING) @Column(name = "holder_status", nullable = false, length = 20) private HolderStatus holderStatus = HolderStatus.ACTIVE;
    @Column(name = "added_at", nullable = false) private LocalDateTime addedAt;
    @Column(name = "removed_at") private LocalDateTime removedAt;

    protected AccountHolder() { }
    @jakarta.persistence.PrePersist void beforeInsert() { if (addedAt == null) addedAt = LocalDateTime.now(); }
}
