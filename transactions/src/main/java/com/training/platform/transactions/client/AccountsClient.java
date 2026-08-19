package com.training.platform.transactions.client;

import com.training.platform.auditclient.AuditClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

/** HTTP client for the accounts service's atomic balance-posting endpoint. */
@Component
public class AccountsClient {
    private final RestClient restClient;

    public AccountsClient(@Qualifier("loadBalancedRestClientBuilder") RestClient.Builder builder,
                          @Value("${services.accounts.base-url}") String baseUrl,
                          @Value("${services.accounts.internal-key}") String internalKey,
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

    public AccountDetailsResponse getAccount(String accountId) {
        try {
            AccountDetailsResponse response = restClient.get()
                    .uri("/api/accounts/{accountId}", accountId)
                    .retrieve()
                    .body(AccountDetailsResponse.class);
            if (response == null) throw new AccountPostingException("ACCOUNT_RESPONSE_INVALID",
                    "Accounts service returned an empty account response");
            return response;
        } catch (RestClientResponseException exception) {
            throw postingException(exception, "ACCOUNT_NOT_FOUND", "ACCOUNT_LOOKUP_REJECTED");
        } catch (ResourceAccessException exception) {
            throw new AccountPostingException("ACCOUNT_SERVICE_UNAVAILABLE", exception.getMessage());
        }
    }

    public List<InterestBearingAccountResponse> interestDue(LocalDate asOf) {
        try {
            InterestBearingAccountResponse[] response = restClient.get()
                    .uri(uri -> uri.path("/api/internal/accounts/interest-due")
                            .queryParam("asOf", asOf).build())
                    .retrieve()
                    .body(InterestBearingAccountResponse[].class);
            return response == null ? List.of() : Arrays.asList(response);
        } catch (RestClientResponseException exception) {
            throw postingException(exception, "ACCOUNT_NOT_FOUND", "INTEREST_ACCOUNT_LOOKUP_REJECTED");
        } catch (ResourceAccessException exception) {
            throw new AccountPostingException("ACCOUNT_SERVICE_UNAVAILABLE", exception.getMessage());
        }
    }

    public List<AnnualFeeAccountResponse> annualFeeAccounts() {
        try {
            AnnualFeeAccountResponse[] response = restClient.get()
                    .uri("/api/internal/accounts/annual-fees")
                    .retrieve()
                    .body(AnnualFeeAccountResponse[].class);
            return response == null ? List.of() : Arrays.asList(response);
        } catch (RestClientResponseException exception) {
            throw postingException(exception, "ACCOUNT_NOT_FOUND", "MAINTENANCE_FEE_LOOKUP_REJECTED");
        } catch (ResourceAccessException exception) {
            throw new AccountPostingException("ACCOUNT_SERVICE_UNAVAILABLE", exception.getMessage());
        }
    }

    public void markInterestProcessed(String accountId, LocalDate periodEnd, String transactionRef) {
        try {
            restClient.post()
                    .uri("/api/internal/accounts/{accountId}/interest-processed", accountId)
                    .body(new InterestProcessingRequest(periodEnd, transactionRef))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            throw postingException(exception, "ACCOUNT_NOT_FOUND", "INTEREST_PROCESSING_REJECTED");
        } catch (ResourceAccessException exception) {
            throw new AccountPostingException("ACCOUNT_SERVICE_UNAVAILABLE", exception.getMessage());
        }
    }

    public AccountTransferResponse transfer(AccountTransferRequest request) {
        try {
            AccountTransferResponse response = restClient.post()
                    .uri("/api/accounts/transfers")
                    .body(request)
                    .retrieve()
                    .body(AccountTransferResponse.class);
            if (response == null || response.debitBalanceAfter() == null || response.creditBalanceAfter() == null) {
                throw new AccountPostingException("ACCOUNT_RESPONSE_INVALID",
                        "Accounts service returned an incomplete transfer response");
            }
            return response;
        } catch (RestClientResponseException exception) {
            String code = exception.getStatusCode().value() == HttpStatus.NOT_FOUND.value()
                    ? "ACCOUNT_NOT_FOUND" : "ACCOUNT_TRANSFER_REJECTED";
            String responseBody = exception.getResponseBodyAsString();
            String message = responseBody.isBlank() ? exception.getMessage() : responseBody;
            throw new AccountPostingException(code, message);
        } catch (ResourceAccessException exception) {
            throw new AccountPostingException("ACCOUNT_SERVICE_UNAVAILABLE", exception.getMessage());
        }
    }

    public AccountAdjustmentResponse adjust(String accountId, AccountAdjustmentRequest request) {
        try {
            AccountAdjustmentResponse response = restClient.post()
                    .uri("/api/accounts/{accountId}/adjustments", accountId)
                    .body(request)
                    .retrieve()
                    .body(AccountAdjustmentResponse.class);
            if (response == null || response.balanceAfter() == null) {
                throw new AccountPostingException("ACCOUNT_RESPONSE_INVALID",
                        "Accounts service returned an incomplete adjustment response");
            }
            return response;
        } catch (RestClientResponseException exception) {
            String code = exception.getStatusCode().value() == HttpStatus.NOT_FOUND.value()
                    ? "ACCOUNT_NOT_FOUND" : "ACCOUNT_ADJUSTMENT_REJECTED";
            String responseBody = exception.getResponseBodyAsString();
            String message = responseBody.isBlank() ? exception.getMessage() : responseBody;
            throw new AccountPostingException(code, message);
        } catch (ResourceAccessException exception) {
            throw new AccountPostingException("ACCOUNT_SERVICE_UNAVAILABLE", exception.getMessage());
        }
    }

    private AccountPostingException postingException(RestClientResponseException exception,
                                                     String notFoundCode, String rejectedCode) {
        String code = exception.getStatusCode().value() == HttpStatus.NOT_FOUND.value()
                ? notFoundCode : rejectedCode;
        String responseBody = exception.getResponseBodyAsString();
        String message = responseBody.isBlank() ? exception.getMessage() : responseBody;
        return new AccountPostingException(code, message);
    }
}
