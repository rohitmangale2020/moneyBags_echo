package com.training.platform.transactions.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "ledger_account")
public class LedgerAccount {
    @Id
    @Column(name = "ledger_account_id", length = 36)
    private String ledgerAccountId;

    @Column(nullable = false, unique = true, length = 60)
    private String code;

    @Column(nullable = false, length = 160)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 20)
    private LedgerAccountType accountType;

    @Column(name = "current_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal currentBalance = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean active = true;

    @jakarta.persistence.PrePersist
    void beforeInsert() {
        if (ledgerAccountId == null) ledgerAccountId = UUID.randomUUID().toString();
        if (currentBalance == null) currentBalance = BigDecimal.ZERO;
    }

    public String getLedgerAccountId() { return ledgerAccountId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public LedgerAccountType getAccountType() { return accountType; }
    public BigDecimal getCurrentBalance() { return currentBalance; }
    public boolean isActive() { return active; }

    public void setCode(String code) { this.code = code; }
    public void setName(String name) { this.name = name; }
    public void setAccountType(LedgerAccountType accountType) { this.accountType = accountType; }
    public void setActive(boolean active) { this.active = active; }

    public void apply(LedgerEntryType entryType, BigDecimal amount) {
        boolean increasesBalance = accountType.hasDebitNormalBalance()
                ? entryType == LedgerEntryType.DEBIT : entryType == LedgerEntryType.CREDIT;
        currentBalance = increasesBalance ? currentBalance.add(amount) : currentBalance.subtract(amount);
    }
}
