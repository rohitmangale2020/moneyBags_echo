package com.training.platform.transactions.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Resolves a customer display name for immutable transaction descriptions. */
@Component
public class CustomersClient {
    private final RestClient restClient;

    public CustomersClient(RestClient.Builder builder,
                           @Value("${services.customers.base-url}") String baseUrl) {
        this.restClient = builder.clone()
                .baseUrl(baseUrl)
                .requestInterceptor((request, body, execution) -> {
                    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                    if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
                        request.getHeaders().setBearerAuth(jwtAuthentication.getToken().getTokenValue());
                    }
                    return execution.execute(request, body);
                })
                .build();
    }

    public String displayName(String customerId) {
        if (customerId == null || customerId.isBlank()) return "ACCOUNT HOLDER";
        try {
            CustomerSummary customer = restClient.get()
                    .uri("/api/customers/{customerId}", customerId)
                    .retrieve()
                    .body(CustomerSummary.class);
            if (customer != null) {
                String name = ((customer.firstName() == null ? "" : customer.firstName()) + " "
                        + (customer.lastName() == null ? "" : customer.lastName())).trim();
                if (!name.isBlank()) return name;
            }
        } catch (RuntimeException ignored) {
            // A customer-name lookup must never turn a successful balance posting
            // into a failed transaction. Legacy customer references may not resolve.
        }
        return "CUSTOMER " + customerId;
    }

    private record CustomerSummary(String firstName, String lastName) { }
}
