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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** An immutable debit or credit posting. Entries sharing a transaction reference must balance. */
@Entity
@Table(name = "ledger_entry", uniqueConstraints = @UniqueConstraint(
        name = "uk_ledger_entry_transaction_line", columnNames = {"transaction_ref", "line_number"}), indexes = {
        @Index(name = "idx_ledger_entry_account_date", columnList = "ledger_account_id, posting_date"),
        @Index(name = "idx_ledger_entry_reference", columnList = "transaction_ref")
})
public class LedgerEntry {
    @Id
    @Column(name = "ledger_entry_id", length = 36)
    private String ledgerEntryId;

    @Column(name = "transaction_ref", nullable = false, length = 40)
    private String transactionRef;

    @Column(name = "line_number", nullable = false)
    private int lineNumber;

    @ManyToOne(optional = false)
    @JoinColumn(name = "ledger_account_id", nullable = false)
    private LedgerAccount ledgerAccount;

    @Column(name = "customer_account_id", length = 36)
    private String customerAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 10)
    private LedgerEntryType entryType;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "posting_date", nullable = false)
    private LocalDate postingDate;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private LedgerEntryStatus status = LedgerEntryStatus.POSTED;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @jakarta.persistence.PrePersist
    void beforeInsert() {
        if (ledgerEntryId == null) ledgerEntryId = UUID.randomUUID().toString();
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (status == null) status = LedgerEntryStatus.POSTED;
    }

    public String getLedgerEntryId() { return ledgerEntryId; }
    public String getTransactionRef() { return transactionRef; }
    public int getLineNumber() { return lineNumber; }
    public LedgerAccount getLedgerAccount() { return ledgerAccount; }
    public String getCustomerAccountId() { return customerAccountId; }
    public LedgerEntryType getEntryType() { return entryType; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrencyCode() { return currencyCode; }
    public LocalDate getPostingDate() { return postingDate; }
    public String getDescription() { return description; }
    public LedgerEntryStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setTransactionRef(String transactionRef) { this.transactionRef = transactionRef; }
    public void setLineNumber(int lineNumber) { this.lineNumber = lineNumber; }
    public void setLedgerAccount(LedgerAccount ledgerAccount) { this.ledgerAccount = ledgerAccount; }
    public void setCustomerAccountId(String customerAccountId) { this.customerAccountId = customerAccountId; }
    public void setEntryType(LedgerEntryType entryType) { this.entryType = entryType; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }
    public void setPostingDate(LocalDate postingDate) { this.postingDate = postingDate; }
    public void setDescription(String description) { this.description = description; }
}
