package com.training.platform.transactions.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** Contractual snapshot for one funded fixed-deposit account. */
@Entity
@Table(name = "fixed_deposit_contract",
        uniqueConstraints = @UniqueConstraint(name = "uk_fd_contract_account", columnNames = "fd_account_id"),
        indexes = @Index(name = "idx_fd_contract_maturity", columnList = "contract_status, maturity_date"))
public class FixedDepositContract {
    @Id @Column(name = "contract_id", length = 36) private String contractId;
    @Column(name = "fd_account_id", nullable = false, length = 36) private String fdAccountId;
    @Column(name = "funding_account_id", nullable = false, length = 36) private String fundingAccountId;
    @Column(name = "payout_account_id", nullable = false, length = 36) private String payoutAccountId;
    @Column(name = "customer_id", nullable = false, length = 36) private String customerId;
    @Column(name = "product_id", nullable = false, length = 36) private String productId;
    @Column(nullable = false, precision = 19, scale = 4) private BigDecimal principal;
    @Column(name = "annual_interest_rate", nullable = false, precision = 8, scale = 4) private BigDecimal annualInterestRate;
    @Column(name = "tenure_months", nullable = false) private Integer tenureMonths;
    @Column(name = "lock_in_period_months", nullable = false) private Integer lockInPeriodMonths;
    @Column(name = "opened_on", nullable = false) private LocalDate openedOn;
    @Column(name = "lock_in_until", nullable = false) private LocalDate lockInUntil;
    @Column(name = "maturity_date", nullable = false) private LocalDate maturityDate;
    @Column(name = "maturity_instruction", nullable = false, length = 50) private String maturityInstruction;
    @Column(name = "premature_withdrawal_allowed", nullable = false) private Boolean prematureWithdrawalAllowed;
    @Enumerated(EnumType.STRING) @Column(name = "contract_status", nullable = false, length = 30) private FixedDepositStatus status;
    @Column(name = "interest_paid", precision = 19, scale = 4) private BigDecimal interestPaid;
    @Column(name = "funding_transaction_id", length = 36) private String fundingTransactionId;
    @Column(name = "closure_transaction_id", length = 36) private String closureTransactionId;
    @Column(name = "interest_transaction_id", length = 36) private String interestTransactionId;
    @Column(name = "closed_at") private LocalDateTime closedAt;
    @Version @Column(name = "version_no", nullable = false) private Long versionNo;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;

    public FixedDepositContract() { }

    public static FixedDepositContract open(String fdAccountId, String fundingAccountId,
                                            String payoutAccountId, String customerId,
                                            String productId, BigDecimal principal,
                                            BigDecimal annualInterestRate, int tenureMonths,
                                            int lockInPeriodMonths, String maturityInstruction,
                                            boolean prematureWithdrawalAllowed, LocalDate openedOn) {
        FixedDepositContract contract = new FixedDepositContract();
        contract.contractId = UUID.randomUUID().toString();
        contract.fdAccountId = fdAccountId;
        contract.fundingAccountId = fundingAccountId;
        contract.payoutAccountId = payoutAccountId;
        contract.customerId = customerId;
        contract.productId = productId;
        contract.principal = principal;
        contract.annualInterestRate = annualInterestRate;
        contract.tenureMonths = tenureMonths;
        contract.lockInPeriodMonths = Math.max(0, lockInPeriodMonths);
        contract.openedOn = openedOn;
        contract.lockInUntil = openedOn.plusMonths(contract.lockInPeriodMonths);
        contract.maturityDate = openedOn.plusMonths(tenureMonths);
        contract.maturityInstruction = maturityInstruction;
        contract.prematureWithdrawalAllowed = prematureWithdrawalAllowed;
        contract.status = FixedDepositStatus.PENDING_FUNDING;
        return contract;
    }

    public void recordFunding(String transactionId) {
        this.fundingTransactionId = transactionId;
        this.status = FixedDepositStatus.ACTIVE;
    }

    public void recordFundingFailure(String transactionId) {
        this.fundingTransactionId = transactionId;
        this.status = FixedDepositStatus.FUNDING_FAILED;
    }

    public void useFundingAccountForPayout() {
        this.payoutAccountId = this.fundingAccountId;
    }

    public void close(FixedDepositStatus status, BigDecimal interestPaid,
                      String closureTransactionId, String interestTransactionId) {
        this.status = status;
        this.interestPaid = interestPaid;
        this.closureTransactionId = closureTransactionId;
        this.interestTransactionId = interestTransactionId;
        this.closedAt = LocalDateTime.now();
    }

    @jakarta.persistence.PrePersist void beforeInsert() {
        LocalDateTime now = LocalDateTime.now();
        if (contractId == null) contractId = UUID.randomUUID().toString();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }
    @jakarta.persistence.PreUpdate void beforeUpdate() { updatedAt = LocalDateTime.now(); }

    public String getContractId() { return contractId; }
    public String getFdAccountId() { return fdAccountId; }
    public String getFundingAccountId() { return fundingAccountId; }
    public String getPayoutAccountId() { return payoutAccountId; }
    public String getCustomerId() { return customerId; }
    public String getProductId() { return productId; }
    public BigDecimal getPrincipal() { return principal; }
    public BigDecimal getAnnualInterestRate() { return annualInterestRate; }
    public Integer getTenureMonths() { return tenureMonths; }
    public Integer getLockInPeriodMonths() { return lockInPeriodMonths; }
    public LocalDate getOpenedOn() { return openedOn; }
    public LocalDate getLockInUntil() { return lockInUntil; }
    public LocalDate getMaturityDate() { return maturityDate; }
    public String getMaturityInstruction() { return maturityInstruction; }
    public Boolean getPrematureWithdrawalAllowed() { return prematureWithdrawalAllowed; }
    public FixedDepositStatus getStatus() { return status; }
    public BigDecimal getInterestPaid() { return interestPaid; }
    public String getFundingTransactionId() { return fundingTransactionId; }
    public String getClosureTransactionId() { return closureTransactionId; }
    public String getInterestTransactionId() { return interestTransactionId; }
    public LocalDateTime getClosedAt() { return closedAt; }
    public Long getVersionNo() { return versionNo; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
