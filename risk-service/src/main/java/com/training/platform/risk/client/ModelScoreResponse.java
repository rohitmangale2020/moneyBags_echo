package com.training.platform.risk.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record ModelScoreResponse(
        @JsonProperty("model_coverage") boolean modelCoverage,
        @JsonProperty("calibrated_lightgbm_score") BigDecimal calibratedLightgbmScore,
        @JsonProperty("isolation_forest_score") BigDecimal isolationForestScore,
        @JsonProperty("final_risk_score") BigDecimal finalRiskScore,
        @JsonProperty("risk_level") String riskLevel,
        @JsonProperty("review_recommended") boolean reviewRecommended,
        List<String> reasons,
        @JsonProperty("model_versions") Map<String, String> modelVersions) { }
