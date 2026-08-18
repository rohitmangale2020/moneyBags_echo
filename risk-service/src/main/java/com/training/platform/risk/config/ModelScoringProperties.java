package com.training.platform.risk.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "services.model-scoring")
public record ModelScoringProperties(String baseUrl) { }
