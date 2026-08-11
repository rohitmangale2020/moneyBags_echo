package com.training.platform.accounts.entity;

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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "account", uniqueConstraints = @UniqueConstraint(name = "uk_account_number", columnNames = "account_number"), indexes = {
        @Index(name = "idx_account_customer", columnList = "customer_id"),
        @Index(name = "idx_account_product", columnList = "product_id")
})
/** Represents a bank account, including balances, ownership, and lifecycle details. */
public class Account {
    @Id @Column(name = "account_id", length = 36) private String accountId;
    @Column(name = "account_number", nullable = false, length = 24) private String accountNumber;
    @Column(name = "customer_id", nullable = false, length = 36) private String customerId;
    @Column(name = "product_id", nullable = false, length = 36) private String productId;
    @Enumerated(EnumType.STRING) @Column(name = "ownership_type", nullable = false, length = 20) private OwnershipType ownershipType;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private AccountStatus status;
    @Column(name = "currency_code", nullable = false, length = 3) private String currencyCode;
    @Column(name = "available_balance", nullable = false, precision = 19, scale = 4) private BigDecimal availableBalance = BigDecimal.ZERO;
    @Column(name = "opened_at", nullable = false) private LocalDateTime openedAt;
    @Column(name = "closed_at") private LocalDateTime closedAt;
    @jakarta.persistence.Version @Column(name = "version_no", nullable = false) private Long versionNo;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
    @OneToMany(mappedBy = "account", cascade = CascadeType.ALL, orphanRemoval = true) private List<AccountHolder> holders = new ArrayList<>();
    @OneToMany(mappedBy = "account", cascade = CascadeType.ALL, orphanRemoval = true) private List<AccountStatusHistory> statusHistory = new ArrayList<>();

    public Account() { }
    @jakarta.persistence.PrePersist void beforeInsert() { LocalDateTime now = LocalDateTime.now(); if (accountId == null) accountId = UUID.randomUUID().toString(); if (openedAt == null) openedAt = now; if (createdAt == null) createdAt = now; updatedAt = now; }
    @jakarta.persistence.PreUpdate void beforeUpdate() { updatedAt = LocalDateTime.now(); }

    public OwnershipType getOwnershipType() { return ownershipType; }
    public AccountStatus getStatus() { return status; }
    public String getAccountId() { return accountId; }
    public String getAccountNumber() { return accountNumber; }
    public String getCustomerId() { return customerId; }
    public String getProductId() { return productId; }
    public String getCurrencyCode() { return currencyCode; }
    public BigDecimal getAvailableBalance() { return availableBalance; }
    public LocalDateTime getOpenedAt() { return openedAt; }
    public LocalDateTime getClosedAt() { return closedAt; }
    public Long getVersionNo() { return versionNo; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public void setProductId(String productId) { this.productId = productId; }
    public void setOwnershipType(OwnershipType ownershipType) { this.ownershipType = ownershipType; }
    public void setStatus(AccountStatus status) { this.status = status; }
    public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }
    public void setAvailableBalance(BigDecimal availableBalance) { this.availableBalance = availableBalance; }
    public void setClosedAt(LocalDateTime closedAt) { this.closedAt = closedAt; }
}
