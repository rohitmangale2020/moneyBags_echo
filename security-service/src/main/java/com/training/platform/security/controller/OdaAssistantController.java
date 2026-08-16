package com.training.platform.security.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Read-only banking context API intended for an Oracle Digital Assistant custom component.
 * The endpoint deliberately returns evidence and policy decisions so a chat response can be
 * explained to a banker or customer rather than acting as an opaque recommendation engine.
 */
@RestController
@RequestMapping("/oda/assistant")
public class OdaAssistantController {
    private final RestClient customers;
    private final RestClient accounts;
    private final RestClient transactions;
    private final RestClient products;

    public OdaAssistantController(RestClient.Builder builder,
                                  @Value("${oda-assistant.customers-url:http://customers-service}") String customersUrl,
                                  @Value("${oda-assistant.accounts-url:http://accounts-service}") String accountsUrl,
                                  @Value("${oda-assistant.transactions-url:http://transactions-service}") String transactionsUrl,
                                  @Value("${oda-assistant.products-url:http://products-service}") String productsUrl) {
        customers = builder.baseUrl(customersUrl).build();
        accounts = builder.baseUrl(accountsUrl).build();
        transactions = builder.baseUrl(transactionsUrl).build();
        products = builder.baseUrl(productsUrl).build();
    }

    @PostMapping("/chat")
    public AssistantResponse chat(@Valid @RequestBody AssistantRequest request, JwtAuthenticationToken authentication) {
        String message = request.message().toLowerCase(Locale.ROOT);
        String token = authentication.getToken().getTokenValue();
        if (mentionsCustomerBriefing(message)) return customerBriefing(request.customerId(), token, authentication);
        if (mentionsTransactionReview(message)) return reviewTransaction(request.transactionId(), token, authentication);
        if (mentionsAccountOverview(message) || (request.accountId() != null && !request.accountId().isBlank())) return accountOverview(request.accountId(), token, authentication);
        if (isRecommendation(message)) return recommendProducts(request.customerId(), token, authentication);
        if (mentionsPolicy(message)) return policyAnswer(message, request.module());
        if (request.transactionId() != null && !request.transactionId().isBlank()) return reviewTransaction(request.transactionId(), token, authentication);
        if (request.customerId() != null) return customerBriefing(request.customerId(), token, authentication);
        return guidedOperation(message, authentication);
    }

    private AssistantResponse customerBriefing(Long customerId, String token, JwtAuthenticationToken authentication) {
        requireEmployee(authentication, "Customer 360 briefings");
        if (customerId == null) throw new AssistantInputException("Provide customerId to prepare a Customer 360 briefing.");
        Customer customer = get(customers, "/api/customers/{id}", token, Customer.class, customerId);
        List<Account> customerAccounts = getList(accounts, "/api/accounts?customerId={id}", token, Account[].class, customerId);
        BigDecimal totalBalance = customerAccounts.stream().map(Account::availableBalance).filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<Evidence> evidence = new ArrayList<>();
        evidence.add(new Evidence("customers-service", "customer", customer.customerId().toString(),
                customer.firstName() + " " + customer.lastName() + ", status " + customer.status()));
        evidence.add(new Evidence("accounts-service", "accounts", String.valueOf(customerAccounts.size()),
                "Total available balance " + totalBalance + " across " + customerAccounts.size() + " account(s)."));
        String answer = "Customer 360 for " + customer.firstName() + " " + customer.lastName() + ": "
                + customerAccounts.size() + " account(s), total available balance " + totalBalance + ".";
        List<String> steps = customerAccounts.isEmpty()
                ? List.of("Confirm the customer's onboarding status.", "Offer an eligible active product after needs assessment.")
                : List.of("Confirm the customer's request and identity.", "Review account status before making any change.", "Use the relevant platform workflow; this assistant does not execute changes.");
        return new AssistantResponse("CUSTOMER_360", answer, evidence, steps, List.of(), policy("ALLOW", "Employee or admin role and customerId are required."));
    }

    private AssistantResponse reviewTransaction(String transactionId, String token, JwtAuthenticationToken authentication) {
        requireEmployee(authentication, "Transaction reviews");
        if (transactionId == null || transactionId.isBlank()) throw new AssistantInputException("Provide transactionId to review a transaction.");
        Transaction transaction = get(transactions, "/api/transactions/{id}", token, Transaction.class, transactionId);
        List<String> explanation = new ArrayList<>();
        explanation.add("Status is " + transaction.transactionStatus() + ".");
        explanation.add("Amount is " + transaction.amount() + " " + transaction.currencyCode() + ".");
        if (transaction.failureReason() != null && !transaction.failureReason().isBlank()) explanation.add("Recorded failure reason: " + transaction.failureReason() + ".");
        if (transaction.feeAmount() != null && transaction.feeAmount().signum() > 0) explanation.add("Recorded fee: " + transaction.feeAmount() + ".");
        List<String> steps = "FAILED".equals(String.valueOf(transaction.transactionStatus()))
                ? List.of("Validate the failure reason.", "Confirm account status and available balance.", "Follow the approved exception or retry procedure; do not create a duplicate transaction.")
                : List.of("Confirm the transaction reference with the customer.", "Review the source and destination account identifiers.", "Use the disputes workflow if the customer contests the transaction.");
        return new AssistantResponse("TRANSACTION_REVIEW", String.join(" ", explanation),
                List.of(new Evidence("transactions-service", "transaction", transaction.transactionId(), "Reference " + transaction.transactionRef())),
                steps, List.of(), policy("ALLOW", "Employee or admin role and a transactionId are required."));
    }

    private AssistantResponse accountOverview(String accountId, String token, JwtAuthenticationToken authentication) {
        requireEmployee(authentication, "Account overviews");
        if (accountId == null || accountId.isBlank()) throw new AssistantInputException("Provide accountId to review an account.");
        Account account = get(accounts, "/api/accounts/{id}", token, Account.class, accountId);
        String answer = "Account " + account.accountNumber() + " is " + account.status() + " with available balance "
                + account.availableBalance() + " " + account.currencyCode() + ".";
        return new AssistantResponse("ACCOUNT_OVERVIEW", answer,
                List.of(new Evidence("accounts-service", "account", account.accountId(), "Account status " + account.status())),
                List.of("Confirm the customer and account context.", "Review holds, status, and balance before proposing an operation.", "Use the approved account workflow for any change."), List.of(),
                policy("ALLOW", "Employee or admin role and an accountId are required."));
    }

    private AssistantResponse policyAnswer(String message, String module) {
        String topic = message.contains("kyc") ? "KYC" : message.contains("fraud") ? "FRAUD" : message.contains("approval") ? "APPROVAL" : "SECURITY";
        String answer = switch (topic) {
            case "KYC" -> "KYC changes require verified supporting documents and the approved customer workflow. Do not activate, open, or modify an account based only on chat input.";
            case "FRAUD" -> "For suspected fraud, preserve the transaction reference, verify the customer through approved channels, and follow the case or account-control workflow. This assistant cannot freeze an account.";
            case "APPROVAL" -> "Approval controls cannot be bypassed. Submit the operation through its normal workflow with complete evidence and wait for the required approver.";
            default -> "Protect customer data, verify identity before sensitive operations, and use only approved workflows. The assistant provides guidance, not authorization.";
        };
        return new AssistantResponse("POLICY_" + topic, answer, List.of(),
                List.of("Review the relevant policy in the " + (module == null || module.isBlank() ? "current module" : module) + " workspace.", "Record the required evidence before continuing."), List.of(),
                policy("GUIDANCE_ONLY", "Policy information is general guidance and does not replace an approval decision."));
    }

    private AssistantResponse recommendProducts(Long customerId, String token, JwtAuthenticationToken authentication) {
        List<Product> active = getList(products, "/api/v1/products", token, Product[].class).stream()
                .filter(product -> "ACTIVE".equalsIgnoreCase(product.status())).toList();
        if (customerId == null) return generalRecommendations(active);
        requireEmployee(authentication, "Customer-specific product recommendations");
        List<Account> customerAccounts = getList(accounts, "/api/accounts?customerId={id}", token, Account[].class, customerId);
        BigDecimal totalBalance = customerAccounts.stream().map(Account::availableBalance).filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<Transaction> history = customerAccounts.stream().flatMap(account -> transactionHistory(account.accountId(), token).stream())
                .collect(java.util.stream.Collectors.collectingAndThen(java.util.stream.Collectors.toMap(Transaction::transactionId, transaction -> transaction, (left, right) -> left, java.util.LinkedHashMap::new), map -> List.copyOf(map.values())));
        long completedTransactions = history.stream().filter(transaction -> "COMPLETED".equalsIgnoreCase(transaction.transactionStatus())).count();
        List<Product> ranked = rankProducts(active, totalBalance, completedTransactions);
        List<Recommendation> recommendations = ranked.stream().limit(3).map(product -> new Recommendation(product.productCode(), product.productName(),
                recommendationReason(product, totalBalance, completedTransactions))).toList();
        return new AssistantResponse("PERSONALISED_PRODUCT_RECOMMENDATION", "Recommendations use the customer's total available balance of " + totalBalance
                + " and " + completedTransactions + " completed transaction(s). Confirm suitability, affordability, and consent before opening an account.",
                List.of(new Evidence("accounts-service", "customer-balance", customerId.toString(), "Total available balance " + totalBalance),
                        new Evidence("transactions-service", "transaction-history", customerId.toString(), completedTransactions + " completed transaction(s) reviewed.")),
                List.of("Ask about the customer's savings goal, liquidity needs, and preferred term.", "Compare eligibility, fees, rate, and withdrawal terms.", "Obtain customer consent in the approved account-opening flow."), recommendations,
                policy("REVIEW_REQUIRED", "Balance and transaction patterns support a recommendation; this is not personalised financial advice."));
    }

    private AssistantResponse generalRecommendations(List<Product> active) {
        List<Recommendation> recommendations = active.stream().sorted(Comparator.comparing(Product::productTypeCode).thenComparing(Product::productName)).limit(3)
                .map(product -> new Recommendation(product.productCode(), product.productName(), "Active " + product.productTypeCode() + " product; minimum balance " + value(product.minimumBalance()) + ", rate " + (product.rate() == null ? "not configured" : value(product.rate().interestRate())) + ".")).toList();
        return new AssistantResponse("PRODUCT_RECOMMENDATION", "Provide a Customer ID for balance- and transaction-informed recommendations. These active products are shown for general discussion.",
                active.stream().map(product -> new Evidence("products-service", "product", product.productCode(), product.status())).toList(), List.of("Ask about the customer's savings goal, liquidity needs, and preferred term.", "Compare eligibility, fees, rate, and withdrawal terms.", "Obtain customer consent in the approved account-opening flow."), recommendations, policy("REVIEW_REQUIRED", "Recommendations are informational and are not personalised financial advice."));
    }

    private List<Transaction> transactionHistory(String accountId, String token) {
        List<Transaction> debits = getList(transactions, "/api/transactions?debitAccountId={id}", token, Transaction[].class, accountId);
        List<Transaction> credits = getList(transactions, "/api/transactions?creditAccountId={id}", token, Transaction[].class, accountId);
        return java.util.stream.Stream.concat(debits.stream(), credits.stream()).toList();
    }
    private List<Product> rankProducts(List<Product> products, BigDecimal balance, long transactions) {
        return products.stream().sorted(Comparator.<Product>comparingInt(product -> productScore(product, balance, transactions)).reversed().thenComparing(Product::productName)).toList();
    }
    private int productScore(Product product, BigDecimal balance, long transactions) {
        String type = product.productTypeCode().toUpperCase(Locale.ROOT);
        int score = 10;
        if (balance.compareTo(new BigDecimal("100000")) >= 0 && type.contains("FD")) score += 100;
        if (transactions >= 3 && type.contains("RD")) score += 80;
        if (balance.compareTo(new BigDecimal("100000")) < 0 && (type.contains("SAV") || type.contains("CURRENT"))) score += 60;
        if (product.minimumBalance() == null || balance.compareTo(product.minimumBalance()) >= 0) score += 20;
        return score;
    }
    private String recommendationReason(Product product, BigDecimal balance, long transactions) {
        String type = product.productTypeCode().toUpperCase(Locale.ROOT);
        if (balance.compareTo(new BigDecimal("100000")) >= 0 && type.contains("FD")) return "Higher available balance supports considering a fixed-deposit option; review liquidity and term requirements.";
        if (transactions >= 3 && type.contains("RD")) return "Regular completed activity supports discussing a recurring-deposit option; confirm the customer's ability to make periodic contributions.";
        if (type.contains("SAV") || type.contains("CURRENT")) return "Available balance and transaction activity support a liquid day-to-day banking option.";
        return "Active product compatible with the reviewed balance; compare its fees, rate, and terms with the customer's needs.";
    }

    private AssistantResponse guidedOperation(String message, JwtAuthenticationToken authentication) {
        boolean privileged = isEmployee(authentication);
        List<String> steps = message.contains("transfer")
                ? List.of("Verify customer identity and transfer details.", "Confirm available balance and beneficiary information.", "Use POST /api/accounts/transfers in the approved workflow.", "Read back the transaction reference; do not repeat a submitted transfer.")
                : message.contains("password")
                ? List.of("Verify identity using the approved process.", "Use the password-reset workflow; never request or disclose a password in chat.", "Confirm completion without revealing credentials.")
                : List.of("Clarify whether the request is for a customer briefing, transaction review, transfer, password help, or products.", "Collect only the identifiers required for that approved workflow.");
        return new AssistantResponse("GUIDED_OPERATION", "I can guide the approved process, but I cannot execute transactions, change customer data, or bypass controls.", List.of(), steps, List.of(),
                policy("GUIDANCE_ONLY", privileged ? "Authenticated staff guidance is available." : "Customer guidance is limited to non-sensitive, read-only information."));
    }

    private boolean mentionsCustomerBriefing(String message) { return message.contains("360") || message.contains("customer brief"); }
    private boolean mentionsTransactionReview(String message) { return message.contains("transaction") || message.contains("payment review"); }
    private boolean mentionsAccountOverview(String message) { return message.contains("account overview") || message.contains("account balance") || message.contains("account status"); }
    private boolean mentionsPolicy(String message) { return message.contains("policy") || message.contains("kyc") || message.contains("fraud") || message.contains("approval") || message.contains("control"); }
    private boolean isRecommendation(String message) { return message.contains("recommend") || message.contains("product") || message.contains("account option"); }
    private boolean isEmployee(JwtAuthenticationToken authentication) { return authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch(Set.of("ROLE_EMPLOYEE", "ROLE_ADMIN")::contains); }
    private void requireEmployee(JwtAuthenticationToken authentication, String capability) { if (!isEmployee(authentication)) throw new AssistantAccessException(capability + " are available only to employees and administrators."); }
    private Policy policy(String decision, String rationale) { return new Policy(decision, rationale); }
    private String value(Object value) { return value == null ? "not configured" : value.toString(); }

    private <T> T get(RestClient client, String uri, String token, Class<T> type, Object... variables) {
        try { return client.get().uri(uri, variables).headers(headers -> headers.setBearerAuth(token)).retrieve().body(type); }
        catch (RestClientException exception) { throw new AssistantUnavailableException("The requested banking data is currently unavailable."); }
    }
    private <T> List<T> getList(RestClient client, String uri, String token, Class<T[]> type, Object... variables) {
        try { T[] result = client.get().uri(uri, variables).headers(headers -> headers.setBearerAuth(token)).retrieve().body(type); return result == null ? List.of() : List.of(result); }
        catch (RestClientException exception) { throw new AssistantUnavailableException("The requested banking data is currently unavailable."); }
    }

    public record AssistantRequest(@NotBlank String message, Long customerId, String transactionId, String accountId, String module) { }
    public record AssistantResponse(String intent, String answer, List<Evidence> evidence, List<String> nextSteps, List<Recommendation> recommendations, Policy policy) { }
    public record Evidence(String source, String type, String id, String summary) { }
    public record Recommendation(String productCode, String productName, String reason) { }
    public record Policy(String decision, String rationale) { }
    private record Customer(Long customerId, String firstName, String lastName, String status) { }
    private record Account(String accountId, String accountNumber, String status, String currencyCode, BigDecimal availableBalance) { }
    private record Transaction(String transactionId, String transactionRef, String transactionStatus, BigDecimal amount, String currencyCode, BigDecimal feeAmount, String failureReason) { }
    private record Product(String productCode, String productName, String productTypeCode, BigDecimal minimumBalance, String status, Rate rate) { }
    private record Rate(BigDecimal interestRate) { }

    @ResponseStatus(HttpStatus.BAD_REQUEST) static class AssistantInputException extends RuntimeException { AssistantInputException(String message) { super(message); } }
    @ResponseStatus(HttpStatus.FORBIDDEN) static class AssistantAccessException extends RuntimeException { AssistantAccessException(String message) { super(message); } }
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE) static class AssistantUnavailableException extends RuntimeException { AssistantUnavailableException(String message) { super(message); } }
}
