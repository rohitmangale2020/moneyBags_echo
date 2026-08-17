package com.training.platform.accounts.client;

import com.training.platform.auditclient.AuditClient;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Checks transaction-owned FD dependencies before an account is closed. */
@Component
public class FixedDepositsClient {
    private final RestClient restClient;

    public FixedDepositsClient(RestClient.Builder builder,
                               @Value("${services.transactions.base-url}") String baseUrl,
                               @Value("${services.internal-key}") String internalKey,
                               AuditClient auditClient) {
        this.restClient = builder.clone()
                .baseUrl(baseUrl)
                .requestInterceptor((request, body, execution) -> {
                    request.getHeaders().set(AuditClient.CORRELATION_HEADER,
                            auditClient.currentCorrelationId());
                    request.getHeaders().set("X-Banking-Internal-Key", internalKey);
                    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                    if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
                        request.getHeaders().setBearerAuth(jwtAuthentication.getToken().getTokenValue());
                    }
                    return execution.execute(request, body);
                })
                .build();
    }

    public List<FixedDepositDependencyResponse> activeForAccount(String accountId) {
        FixedDepositDependencyResponse[] response = restClient.get()
                .uri(uri -> uri.path("/api/internal/fixed-deposits/dependencies")
                        .queryParam("accountId", accountId).build())
                .retrieve()
                .body(FixedDepositDependencyResponse[].class);
        return response == null ? List.of() : Arrays.asList(response);
    }
}
