package com.training.platform.risk.dto;

import com.training.platform.risk.entity.RiskAssessment;
import com.training.platform.risk.entity.RiskLevel;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record RiskAssessmentResponse(
        String assessmentId, String transactionRef, RiskLevel riskLevel, boolean modelCoverage,
        BigDecimal lightgbmScore, BigDecimal isolationForestScore, BigDecimal finalRiskScore, boolean reviewRecommended,
        List<String> reasons, String modelVersions, LocalDateTime assessedAt) {
    public static RiskAssessmentResponse from(RiskAssessment assessment) {
        List<String> reasons = assessment.getReasons() == null || assessment.getReasons().isBlank()
                ? List.of() : List.of(assessment.getReasons().split("\\n"));
        return new RiskAssessmentResponse(assessment.getAssessmentId(), assessment.getTransactionRef(),
                assessment.getRiskLevel(), assessment.isModelCoverage(), assessment.getLightgbmScore(),
                assessment.getIsolationForestScore(), assessment.getFinalRiskScore(), assessment.isReviewRecommended(), reasons,
                assessment.getModelVersions(), assessment.getAssessedAt());
    }
}
