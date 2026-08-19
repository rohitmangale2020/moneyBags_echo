package com.training.platform.risk.service;

import com.training.platform.risk.client.ModelScoreRequest;
import com.training.platform.risk.client.ModelScoreResponse;
import com.training.platform.risk.client.ModelScoringClient;
import com.training.platform.risk.dto.ApprovedTransactionProfileRequest;
import com.training.platform.risk.dto.RiskAssessmentRequest;
import com.training.platform.risk.entity.RecipientRiskProfile;
import com.training.platform.risk.entity.RiskAssessment;
import com.training.platform.risk.entity.RiskLevel;
import com.training.platform.risk.entity.UserRiskProfile;
import com.training.platform.risk.repository.RecipientRiskProfileRepository;
import com.training.platform.risk.repository.RiskAssessmentRepository;
import com.training.platform.risk.repository.UserRiskProfileRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RiskAssessmentService {
    private final RiskAssessmentRepository assessments;
    private final UserRiskProfileRepository users;
    private final RecipientRiskProfileRepository recipients;
    private final ModelScoringClient modelScoringClient;

    public RiskAssessmentService(RiskAssessmentRepository assessments, UserRiskProfileRepository users,
                                 RecipientRiskProfileRepository recipients, ModelScoringClient modelScoringClient) {
        this.assessments = assessments;
        this.users = users;
        this.recipients = recipients;
        this.modelScoringClient = modelScoringClient;
    }

    @Transactional
    public RiskAssessment assess(RiskAssessmentRequest request) {
        String recipientKey = recipientKey(request.creditAccountId(), request.externalBeneficiary());
        RecipientRiskProfile recipient = recipientKey == null ? null : recipients.findById(recipientKey).orElse(null);
        try {
            ModelScoreResponse score = modelScoringClient.score(new ModelScoreRequest(
                    request.transactionRef(), request.transactionType(), request.amount(), request.oldBalanceOrg(),
                    request.oldBalanceDest(), recipient == null ? 0 : recipient.getApprovedTransactionCount(),
                    recipient == null ? BigDecimal.ZERO : recipient.getApprovedTotalAmount(),
                    DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(
                            request.occurredAt() == null ? LocalDateTime.now() : request.occurredAt())));
            return assessments.save(fromModelResponse(request, score));
        } catch (RuntimeException exception) {
            // Keep the assessment auditable. The transaction service can choose its policy for an unavailable scorer.
            RiskAssessment unavailable = basicAssessment(request);
            unavailable.setRiskLevel(RiskLevel.RISK_UNAVAILABLE);
            unavailable.setReviewRecommended(true);
            unavailable.setReasons("Risk scoring service unavailable: " + limit(exception.getMessage(), 500));
            unavailable.setModelVersions("");
            return assessments.save(unavailable);
        }
    }

    @Transactional
    public void recordApprovedTransaction(ApprovedTransactionProfileRequest request) {
        UserRiskProfile user = users.findById(request.customerId()).orElseGet(() -> new UserRiskProfile(request.customerId()));
        user.recordApproved(request.amount(), request.completedAt());
        users.save(user);

        String recipientKey = recipientKey(request.creditAccountId(), request.externalBeneficiary());
        if (recipientKey != null) {
            RecipientRiskProfile recipient = recipients.findById(recipientKey)
                    .orElseGet(() -> new RecipientRiskProfile(recipientKey));
            recipient.recordApproved(request.amount());
            recipients.save(recipient);
        }
    }

    @Transactional(readOnly = true)
    public List<RiskAssessment> assessmentsFor(String transactionRef) {
        return assessments.findByTransactionRefOrderByAssessedAtDesc(transactionRef);
    }

    private RiskAssessment fromModelResponse(RiskAssessmentRequest request, ModelScoreResponse score) {
        RiskAssessment assessment = basicAssessment(request);
        assessment.setRiskLevel(toRiskLevel(score.riskLevel()));
        assessment.setModelCoverage(score.modelCoverage());
        assessment.setLightgbmScore(score.calibratedLightgbmScore());
        assessment.setIsolationForestScore(score.isolationForestScore());
        assessment.setFinalRiskScore(score.finalRiskScore());
        assessment.setReviewRecommended(score.reviewRecommended());
        assessment.setReasons(String.join("\n", score.reasons() == null ? List.of() : score.reasons()));
        assessment.setModelVersions(formatVersions(score.modelVersions()));
        return assessment;
    }

    private RiskAssessment basicAssessment(RiskAssessmentRequest request) {
        RiskAssessment assessment = new RiskAssessment();
        assessment.setTransactionRef(request.transactionRef());
        assessment.setCustomerId(request.initiatedByCustomerId());
        assessment.setTransactionType(request.transactionType());
        assessment.setAmount(request.amount());
        assessment.setModelCoverage(false);
        return assessment;
    }

    private RiskLevel toRiskLevel(String value) {
        try { return RiskLevel.valueOf(value); }
        catch (Exception ignored) { return RiskLevel.RISK_UNAVAILABLE; }
    }

    private String recipientKey(String creditAccountId, String externalBeneficiary) {
        if (creditAccountId != null && !creditAccountId.isBlank()) return "ACCOUNT:" + creditAccountId;
        if (externalBeneficiary != null && !externalBeneficiary.isBlank()) return "BENEFICIARY:" + externalBeneficiary.trim().toUpperCase();
        return null;
    }

    private String formatVersions(Map<String, String> versions) {
        if (versions == null || versions.isEmpty()) return "";
        return versions.entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue()).sorted()
                .reduce((left, right) -> left + ";" + right).orElse("");
    }

    private String limit(String value, int maximum) {
        if (value == null) return "Unknown model scoring error";
        return value.length() <= maximum ? value : value.substring(0, maximum);
    }
}
