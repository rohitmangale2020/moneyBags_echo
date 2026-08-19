package com.training.platform.risk.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.training.platform.risk.client.ModelScoreRequest;
import com.training.platform.risk.client.ModelScoreResponse;
import com.training.platform.risk.client.ModelScoringClient;
import com.training.platform.risk.dto.RiskAssessmentRequest;
import com.training.platform.risk.entity.RiskAssessment;
import com.training.platform.risk.entity.RiskLevel;
import com.training.platform.risk.repository.RecipientRiskProfileRepository;
import com.training.platform.risk.repository.RiskAssessmentRepository;
import com.training.platform.risk.repository.UserRiskProfileRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RiskAssessmentServiceTest {
    @Mock private RiskAssessmentRepository assessments;
    @Mock private UserRiskProfileRepository users;
    @Mock private RecipientRiskProfileRepository recipients;
    @Mock private ModelScoringClient modelScoringClient;
    private RiskAssessmentService service;

    @BeforeEach
    void setUp() {
        service = new RiskAssessmentService(assessments, users, recipients, modelScoringClient);
        when(assessments.save(any(RiskAssessment.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void scoresOpeningDepositAsDepositButKeepsItsBankingTransactionType() {
        when(modelScoringClient.score(any(ModelScoreRequest.class))).thenReturn(new ModelScoreResponse(
                false, null, null, null, "NOT_SCORED", false,
                List.of("Transaction type is outside the current PaySim-trained model scope."), Map.of()));
        RiskAssessmentRequest request = new RiskAssessmentRequest(
                "OPEN-account-1", "OPENING_DEPOSIT", new BigDecimal("1000.00"), "INR",
                null, "account-1", null, "customer-1", BigDecimal.ZERO, BigDecimal.ZERO,
                LocalDateTime.of(2026, 8, 19, 12, 0));

        RiskAssessment assessment = service.assess(request);

        ArgumentCaptor<ModelScoreRequest> modelRequest = ArgumentCaptor.forClass(ModelScoreRequest.class);
        verify(modelScoringClient).score(modelRequest.capture());
        assertEquals("DEPOSIT", modelRequest.getValue().transactionType());
        assertEquals("OPENING_DEPOSIT", assessment.getTransactionType());
        assertEquals(RiskLevel.NOT_SCORED, assessment.getRiskLevel());
        assertFalse(assessment.isReviewRecommended());
    }
}
