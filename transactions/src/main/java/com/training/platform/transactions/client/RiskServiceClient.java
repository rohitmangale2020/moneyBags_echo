package com.training.platform.transactions.client;

import com.training.platform.auditclient.AuditClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class RiskServiceClient {
    private final RestClient client;

    public RiskServiceClient(@Qualifier("directRestClientBuilder") RestClient.Builder builder,
                             @Value("${services.risk.base-url}") String baseUrl, AuditClient audit) {
        this.client = builder.clone().baseUrl(baseUrl).requestInterceptor((request, body, execution) -> {
            request.getHeaders().set(AuditClient.CORRELATION_HEADER, audit.currentCorrelationId());
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication instanceof JwtAuthenticationToken jwt) {
                request.getHeaders().setBearerAuth(jwt.getToken().getTokenValue());
            }
            return execution.execute(request, body);
        }).build();
    }

    public RiskAssessmentResponse assess(RiskAssessmentRequest request) {
        return client.post().uri("/api/risk/assessments").body(request).retrieve()
                .body(RiskAssessmentResponse.class);
    }
}
