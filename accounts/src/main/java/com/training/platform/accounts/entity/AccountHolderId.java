package com.training.platform.accounts.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

/** Composite primary key for an account holder: one customer on one account. */
@Embeddable
public class AccountHolderId implements Serializable {
    @Column(name = "account_id", length = 36, nullable = false)
    private String accountId;

    @Column(name = "customer_id", length = 36, nullable = false)
    private String customerId;

    protected AccountHolderId() { }

    public AccountHolderId(String accountId, String customerId) {
        this.accountId = accountId;
        this.customerId = customerId;
    }

    public String getAccountId() { return accountId; }
    public String getCustomerId() { return customerId; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof AccountHolderId that)) return false;
        return Objects.equals(accountId, that.accountId) && Objects.equals(customerId, that.customerId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accountId, customerId);
    }
}
