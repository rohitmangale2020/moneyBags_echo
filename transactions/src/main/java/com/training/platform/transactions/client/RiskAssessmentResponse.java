package com.training.platform.transactions.client;

import java.math.BigDecimal;
import java.util.List;

public record RiskAssessmentResponse(String assessmentId, String riskLevel, boolean reviewRecommended,
                                     BigDecimal finalRiskScore, List<String> reasons) { }
