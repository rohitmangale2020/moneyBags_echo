package com.training.platform.accounts.client;

import com.training.platform.auditclient.AuditClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Reads active product rules from the products service. */
@Component
public class ProductsClient {
    private final RestClient restClient;

    public ProductsClient(RestClient.Builder builder,
                          @Value("${services.products.base-url}") String baseUrl,
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

    public ProductRulesResponse getById(String productId) {
        final long numericId;
        try {
            numericId = Long.parseLong(productId);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Product ID must be numeric: " + productId);
        }
        ProductRulesResponse response = restClient.get()
                .uri("/api/v1/products/id/{productId}", numericId)
                .retrieve()
                .body(ProductRulesResponse.class);
        if (response == null) throw new IllegalStateException("Products service returned an empty response");
        return response;
    }
}
