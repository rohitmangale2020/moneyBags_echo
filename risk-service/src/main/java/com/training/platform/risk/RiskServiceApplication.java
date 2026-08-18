package com.training.platform.risk;

import com.training.platform.risk.config.ModelScoringProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(ModelScoringProperties.class)
public class RiskServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(RiskServiceApplication.class, args);
    }
}
