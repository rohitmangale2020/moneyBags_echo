package com.training.platform.risk.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class ModelScoringConfiguration {
    @Bean
    RestClient modelScoringRestClient(ModelScoringProperties properties) {
        return RestClient.builder().baseUrl(properties.baseUrl()).build();
    }
}
