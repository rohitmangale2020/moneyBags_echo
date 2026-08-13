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
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** Immutable account activity entry produced from a completed transaction. */
@Entity
@Table(name = "account_statement", indexes = {
        @Index(name = "idx_statement_account_posted", columnList = "account_id, posted_at"),
        @Index(name = "idx_statement_transaction", columnList = "transaction_id")
})
public class AccountStatement {
    @Id @Column(name = "statement_id", length = 36) private String statementId;
    @ManyToOne(optional = false) @JoinColumn(name = "transaction_id", nullable = false) private BankTransaction transaction;
    @Column(name = "account_id", nullable = false, length = 36) private String accountId;
    @Enumerated(EnumType.STRING) @Column(name = "entry_type", nullable = false, length = 10) private StatementEntryType entryType;
    @Column(nullable = false, precision = 19, scale = 4) private BigDecimal amount;
    @Column(length = 500) private String description;
    @Column(name = "withdrawal_amount", precision = 19, scale = 4) private BigDecimal withdrawalAmount;
    @Column(name = "deposit_amount", precision = 19, scale = 4) private BigDecimal depositAmount;
    @Column(name = "currency_code", nullable = false, length = 3) private String currencyCode;
    @Column(name = "balance_after", nullable = false, precision = 19, scale = 4) private BigDecimal balanceAfter;
    @Column(name = "closing_balance", precision = 19, scale = 4) private BigDecimal closingBalance;
    @Column(name = "posted_at", nullable = false) private LocalDateTime postedAt;

    public AccountStatement() { }

    @jakarta.persistence.PrePersist
    void beforeInsert() {
        if (statementId == null) statementId = UUID.randomUUID().toString();
        if (postedAt == null) postedAt = LocalDateTime.now();
    }

    public String getStatementId() { return statementId; }
    public BankTransaction getTransaction() { return transaction; }
    public String getAccountId() { return accountId; }
    public StatementEntryType getEntryType() { return entryType; }
    public BigDecimal getAmount() { return amount; }
    public String getDescription() { return description; }
    public BigDecimal getWithdrawalAmount() { return withdrawalAmount; }
    public BigDecimal getDepositAmount() { return depositAmount; }
    public String getCurrencyCode() { return currencyCode; }
    public BigDecimal getBalanceAfter() { return balanceAfter; }
    public BigDecimal getClosingBalance() { return closingBalance; }
    public LocalDateTime getPostedAt() { return postedAt; }

    public void setTransaction(BankTransaction transaction) { this.transaction = transaction; }
    public void setAccountId(String accountId) { this.accountId = accountId; }
    public void setEntryType(StatementEntryType entryType) { this.entryType = entryType; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public void setDescription(String description) { this.description = description; }
    public void setWithdrawalAmount(BigDecimal withdrawalAmount) { this.withdrawalAmount = withdrawalAmount; }
    public void setDepositAmount(BigDecimal depositAmount) { this.depositAmount = depositAmount; }
    public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }
    public void setBalanceAfter(BigDecimal balanceAfter) { this.balanceAfter = balanceAfter; }
    public void setClosingBalance(BigDecimal closingBalance) { this.closingBalance = closingBalance; }
}
