package com.training.platform.transactions.client;

import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/** HTTP client for the accounts service's atomic balance-posting endpoint. */
@Component
public class AccountsClient {
    private final RestClient restClient;

    public AccountsClient(RestClient.Builder builder,
                          @Value("${services.accounts.base-url}") String baseUrl,
                          @Value("${services.accounts.username}") String username,
                          @Value("${services.accounts.password}") String password) {
        this.restClient = builder
                .baseUrl(baseUrl)
                .defaultHeaders(headers -> headers.setBasicAuth(username, password, StandardCharsets.UTF_8))
                .build();
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
}
