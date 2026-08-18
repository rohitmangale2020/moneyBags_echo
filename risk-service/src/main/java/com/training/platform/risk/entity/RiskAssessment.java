package com.training.platform.risk.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "risk_assessment", indexes = {
        @Index(name = "idx_risk_assessment_transaction", columnList = "transaction_ref"),
        @Index(name = "idx_risk_assessment_customer", columnList = "customer_id, assessed_at")
})
public class RiskAssessment {
    @Id @Column(name = "assessment_id", length = 36) private String assessmentId;
    @Column(name = "transaction_ref", nullable = false, length = 40) private String transactionRef;
    @Column(name = "customer_id", length = 36) private String customerId;
    @Column(name = "transaction_type", nullable = false, length = 30) private String transactionType;
    @Column(nullable = false, precision = 19, scale = 4) private BigDecimal amount;
    @Enumerated(EnumType.STRING) @Column(name = "risk_level", nullable = false, length = 20) private RiskLevel riskLevel;
    @Column(name = "model_coverage", nullable = false) private boolean modelCoverage;
    @Column(name = "lightgbm_score", precision = 18, scale = 12) private BigDecimal lightgbmScore;
    @Column(name = "isolation_forest_score", precision = 18, scale = 12) private BigDecimal isolationForestScore;
    @Column(name = "final_risk_score", precision = 18, scale = 12) private BigDecimal finalRiskScore;
    @Column(name = "review_recommended", nullable = false) private boolean reviewRecommended;
    @Column(name = "reasons", length = 2000) private String reasons;
    @Column(name = "model_versions", length = 500) private String modelVersions;
    @Column(name = "assessed_at", nullable = false) private LocalDateTime assessedAt;

    public RiskAssessment() { }

    @jakarta.persistence.PrePersist
    void beforeInsert() {
        if (assessmentId == null) assessmentId = UUID.randomUUID().toString();
        if (assessedAt == null) assessedAt = LocalDateTime.now();
    }

    public String getAssessmentId() { return assessmentId; }
    public String getTransactionRef() { return transactionRef; }
    public String getCustomerId() { return customerId; }
    public String getTransactionType() { return transactionType; }
    public BigDecimal getAmount() { return amount; }
    public RiskLevel getRiskLevel() { return riskLevel; }
    public boolean isModelCoverage() { return modelCoverage; }
    public BigDecimal getLightgbmScore() { return lightgbmScore; }
    public BigDecimal getIsolationForestScore() { return isolationForestScore; }
    public BigDecimal getFinalRiskScore() { return finalRiskScore; }
    public boolean isReviewRecommended() { return reviewRecommended; }
    public String getReasons() { return reasons; }
    public String getModelVersions() { return modelVersions; }
    public LocalDateTime getAssessedAt() { return assessedAt; }

    public void setTransactionRef(String value) { transactionRef = value; }
    public void setCustomerId(String value) { customerId = value; }
    public void setTransactionType(String value) { transactionType = value; }
    public void setAmount(BigDecimal value) { amount = value; }
    public void setRiskLevel(RiskLevel value) { riskLevel = value; }
    public void setModelCoverage(boolean value) { modelCoverage = value; }
    public void setLightgbmScore(BigDecimal value) { lightgbmScore = value; }
    public void setIsolationForestScore(BigDecimal value) { isolationForestScore = value; }
    public void setFinalRiskScore(BigDecimal value) { finalRiskScore = value; }
    public void setReviewRecommended(boolean value) { reviewRecommended = value; }
    public void setReasons(String value) { reasons = value; }
    public void setModelVersions(String value) { modelVersions = value; }
}
