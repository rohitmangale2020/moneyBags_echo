package com.training.platform.risk.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "recipient_risk_profile")
public class RecipientRiskProfile {
    @Id @Column(name = "recipient_key", length = 200) private String recipientKey;
    @Column(name = "approved_transaction_count", nullable = false) private long approvedTransactionCount;
    @Column(name = "approved_total_amount", nullable = false, precision = 19, scale = 4) private BigDecimal approvedTotalAmount = BigDecimal.ZERO;

    protected RecipientRiskProfile() { }
    public RecipientRiskProfile(String recipientKey) { this.recipientKey = recipientKey; }
    public void recordApproved(BigDecimal amount) { approvedTransactionCount++; approvedTotalAmount = approvedTotalAmount.add(amount); }
    public long getApprovedTransactionCount() { return approvedTransactionCount; }
    public BigDecimal getApprovedTotalAmount() { return approvedTotalAmount; }
}
