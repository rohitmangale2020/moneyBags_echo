package com.training.platform.auditclient;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class AuditClientAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    AuditClient auditClient(
            @Value("${services.audit.base-url:http://localhost:8086}") String baseUrl,
            @Value("${audit.internal-key:local-audit-key}") String internalKey) {
        return new AuditClient(baseUrl, internalKey);
    }
}
