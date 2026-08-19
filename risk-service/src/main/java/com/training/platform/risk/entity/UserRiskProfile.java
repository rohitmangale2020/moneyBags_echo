package com.training.platform.risk.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_risk_profile")
public class UserRiskProfile {
    @Id @Column(name = "customer_id", length = 36) private String customerId;
    @Column(name = "approved_transaction_count", nullable = false) private long approvedTransactionCount;
    @Column(name = "approved_total_amount", nullable = false, precision = 19, scale = 4) private BigDecimal approvedTotalAmount = BigDecimal.ZERO;
    @Column(name = "last_approved_at") private LocalDateTime lastApprovedAt;

    protected UserRiskProfile() { }
    public UserRiskProfile(String customerId) { this.customerId = customerId; }
    public void recordApproved(BigDecimal amount, LocalDateTime completedAt) {
        approvedTransactionCount++;
        approvedTotalAmount = approvedTotalAmount.add(amount);
        lastApprovedAt = completedAt;
    }
    public long getApprovedTransactionCount() { return approvedTransactionCount; }
    public BigDecimal getApprovedTotalAmount() { return approvedTotalAmount; }
    public LocalDateTime getLastApprovedAt() { return lastApprovedAt; }
}
