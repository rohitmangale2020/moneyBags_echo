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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
    private static final Pattern CUSTOMER_NAME_PATTERN = Pattern.compile("\\bcustomer(?:\\s+(?:named|called))?\\s+([\\p{L}'-]+)(?:\\s+([\\p{L}'-]+))?");
    private static final Set<String> CUSTOMER_QUERY_WORDS = Set.of("account", "details", "information", "info", "profile", "status");
    private static final Set<String> BANKING_TERMS = Set.of(
            "account", "balance", "bank", "banking", "beneficiary", "card", "cash", "credit", "customer",
            "deposit", "dispute", "emi", "fixed deposit", "fraud", "fund", "interest", "kyc", "ledger",
            "loan", "money", "nominee", "overdraft", "password", "payment", "pin", "product", "rate",
            "statement", "transfer", "transaction", "upi", "withdraw", "withdrawal");
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
        if (isRecommendation(message)) return recommendProducts(request.customerId(), token, authentication);
        if (mentionsCustomer(message) || request.customerId() != null) return customerProfile(request.customerId(), message, token, authentication);
        if (!isBankingQuestion(message)) return outOfScopeResponse();
        if (mentionsTransactionReview(message)) return reviewTransaction(request.transactionId(), token, authentication);
        if (mentionsAccountOverview(message) || (request.accountId() != null && !request.accountId().isBlank())) return accountOverview(request.accountId(), token, authentication);
        if (mentionsPolicy(message)) return policyAnswer(message, request.module());
        if (request.transactionId() != null && !request.transactionId().isBlank()) return reviewTransaction(request.transactionId(), token, authentication);
        if (request.customerId() != null) return customerBriefing(request.customerId(), token, authentication);
        return guidedOperation(message, authentication);
    }

    private boolean isBankingQuestion(String message) {
        return BANKING_TERMS.stream().anyMatch(message::contains);
    }

    private AssistantResponse outOfScopeResponse() {
        return new AssistantResponse("OUT_OF_SCOPE",
                "I can help only with banking questions, or customer-specific questions when a Customer ID is provided.",
                List.of(), List.of("Please ask a banking-related question or provide a Customer ID for a customer question."), List.of(),
                policy("RESTRICTED", "This assistant is limited to banking support and authorized customer information."));
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

    private AssistantResponse customerProfile(Long customerId, String message, String token, JwtAuthenticationToken authentication) {
        requireEmployee(authentication, "Customer information");
        Customer customer = resolveCustomer(customerId, message, token);
        String answer = customerAnswer(customer, message);
        return new AssistantResponse("CUSTOMER_PROFILE", answer,
                List.of(new Evidence("customers-service", "customer", customer.customerId().toString(),
                        "Customer profile retrieved for " + customer.firstName() + " " + customer.lastName() + ".")),
                List.of("Use the customer workspace to review or update records through the approved workflow."), List.of(),
                policy("ALLOW", "Employee or admin role plus a Customer ID or an unambiguous customer name are required to view customer information."),
                new CustomerProfile(customer.customerId(), customer.firstName() + " " + customer.lastName(), customer.status(),
                        customer.cifNo(), customer.phone(), customer.email(), customer.occupation(), customer.dob()));
    }

    private Customer resolveCustomer(Long customerId, String message, String token) {
        if (customerId != null) return get(customers, "/api/customers/{id}", token, Customer.class, customerId);
        Matcher matcher = CUSTOMER_NAME_PATTERN.matcher(message);
        if (!matcher.find() || CUSTOMER_QUERY_WORDS.contains(matcher.group(1))) {
            throw new AssistantInputException("Provide a Customer ID or the customer's name, for example: status for customer Rishabh Singh.");
        }
        String firstName = matcher.group(1);
        String lastName = matcher.group(2);
        List<Customer> matches = getList(customers, "/api/customers/search/first-name/{firstName}", token, Customer[].class, firstName)
                .stream()
                .filter(customer -> lastName == null || lastName.equalsIgnoreCase(customer.lastName()))
                .toList();
        if (matches.isEmpty()) throw new AssistantInputException("No customer matched that name. Provide the Customer ID to continue.");
        if (matches.size() > 1) throw new AssistantInputException("More than one customer matched that name. Provide the Customer ID to continue.");
        return matches.get(0);
    }

    private String customerAnswer(Customer customer, String message) {
        String fullName = customer.firstName() + " " + customer.lastName();
        if (message.contains("status")) return "Customer " + fullName + " is " + customer.status() + ".";
        if (message.contains("email")) return "The recorded email for " + fullName + " is " + value(customer.email()) + ".";
        if (message.contains("phone") || message.contains("contact")) return "The recorded phone number for " + fullName + " is " + value(customer.phone()) + ".";
        if (message.contains("occupation")) return "The recorded occupation for " + fullName + " is " + value(customer.occupation()) + ".";
        if (message.contains("dob") || message.contains("birth")) return "The recorded date of birth for " + fullName + " is " + value(customer.dob()) + ".";
        if (message.contains("cif")) return "The CIF for " + fullName + " is " + value(customer.cifNo()) + ".";
        return "Customer snapshot for " + fullName + ": status " + customer.status() + ", CIF " + value(customer.cifNo())
                + ", contact " + value(customer.phone()) + ", " + value(customer.email()) + ", occupation " + value(customer.occupation()) + ".";
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
        if (accountId == null || accountId.isBlank()) throw new AssistantInputException("Provide an Account ID or 12-digit account number to review an account.");
        Account account = resolveAccount(accountId, token);
        String answer = "Account " + account.accountNumber() + " is " + account.status() + " with available balance "
                + account.availableBalance() + " " + account.currencyCode() + ".";
        return new AssistantResponse("ACCOUNT_OVERVIEW", answer,
                List.of(new Evidence("accounts-service", "account", account.accountId(), "Account status " + account.status())),
                List.of("Confirm the customer and account context.", "Review holds, status, and balance before proposing an operation.", "Use the approved account workflow for any change."), List.of(),
                policy("ALLOW", "Employee or admin role and an accountId are required."));
    }

    private Account resolveAccount(String accountReference, String token) {
        if (accountReference.matches("\\d{12}")) {
            List<Account> matches = getList(accounts, "/api/accounts?accountNumber={accountNumber}", token,
                    Account[].class, accountReference);
            if (matches.isEmpty()) throw new AssistantInputException("No account matched that account number.");
            return matches.get(0);
        }
        return get(accounts, "/api/accounts/{id}", token, Account.class, accountReference);
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
        if (customerId == null) throw new AssistantInputException("Provide customerId for recommendations based on balance, transactions, minimum balance, and interest rate.");
        requireEmployee(authentication, "Customer-specific product recommendations");
        List<Account> customerAccounts = getList(accounts, "/api/accounts?customerId={id}", token, Account[].class, customerId);
        BigDecimal totalBalance = customerAccounts.stream().map(Account::availableBalance).filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<Transaction> history = customerAccounts.stream().flatMap(account -> transactionHistory(account.accountId(), token).stream())
                .collect(java.util.stream.Collectors.collectingAndThen(java.util.stream.Collectors.toMap(Transaction::transactionId, transaction -> transaction, (left, right) -> left, java.util.LinkedHashMap::new), map -> List.copyOf(map.values())));
        long completedTransactions = history.stream().filter(transaction -> "COMPLETED".equalsIgnoreCase(transaction.transactionStatus())).count();
        List<Product> eligible = active.stream().filter(product -> meetsMinimumBalance(product, totalBalance)).toList();
        List<Product> ranked = rankProducts(eligible, totalBalance, completedTransactions);
        List<Recommendation> recommendations = ranked.stream().limit(3)
                .map(product -> recommendation(product, totalBalance, completedTransactions)).toList();
        String answer = recommendations.isEmpty()
                ? "No active product currently meets the customer's total available balance of " + totalBalance + ". Review the minimum-balance requirements or discuss a lower-threshold product."
                : "Recommendations use the customer's total available balance of " + totalBalance + ", " + completedTransactions
                + " completed transaction(s), each product's minimum balance, and its configured interest rate. Confirm suitability, affordability, and consent before opening an account.";
        return new AssistantResponse("PERSONALISED_PRODUCT_RECOMMENDATION", answer,
                List.of(new Evidence("accounts-service", "customer-balance", customerId.toString(), "Total available balance " + totalBalance),
                        new Evidence("transactions-service", "transaction-history", customerId.toString(), completedTransactions + " completed transaction(s) reviewed.")),
                List.of("Ask about the customer's savings goal, liquidity needs, and preferred term.", "Compare eligibility, fees, rate, and withdrawal terms.", "Obtain customer consent in the approved account-opening flow."), recommendations,
                policy("REVIEW_REQUIRED", "Balance and transaction patterns support a recommendation; this is not personalised financial advice."));
    }

    private AssistantResponse generalRecommendations(List<Product> active) {
        List<Recommendation> recommendations = active.stream().sorted(Comparator.comparing(Product::productTypeCode).thenComparing(Product::productName)).limit(3)
                .map(product -> recommendation(product, null, 0)).toList();
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
        if (meetsMinimumBalance(product, balance)) score += 20;
        if (product.rate() != null && product.rate().interestRate() != null) score += product.rate().interestRate().multiply(BigDecimal.TEN).intValue();
        return score;
    }
    private boolean meetsMinimumBalance(Product product, BigDecimal balance) { return product.minimumBalance() == null || balance.compareTo(product.minimumBalance()) >= 0; }
    private Recommendation recommendation(Product product, BigDecimal balance, long transactions) {
        BigDecimal rate = product.rate() == null ? null : product.rate().interestRate();
        String reason = balance == null
                ? "Active " + product.productTypeCode() + " product; review its minimum balance and interest rate before recommending it."
                : recommendationReason(product, balance, transactions) + " Eligible at the reviewed balance; minimum balance " + value(product.minimumBalance()) + ", interest rate " + value(rate) + ".";
        return new Recommendation(product.productCode(), product.productName(), reason, product.minimumBalance(), rate);
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
    private boolean mentionsCustomer(String message) { return message.contains("customer") || message.contains("cif") || message.contains("contact") || message.contains("occupation"); }
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
    public record AssistantResponse(String intent, String answer, List<Evidence> evidence, List<String> nextSteps,
                                    List<Recommendation> recommendations, Policy policy, CustomerProfile customerProfile) {
        public AssistantResponse(String intent, String answer, List<Evidence> evidence, List<String> nextSteps,
                                 List<Recommendation> recommendations, Policy policy) {
            this(intent, answer, evidence, nextSteps, recommendations, policy, null);
        }
    }
    public record Evidence(String source, String type, String id, String summary) { }
    public record Recommendation(String productCode, String productName, String reason, BigDecimal minimumBalance, BigDecimal interestRate) { }
    public record Policy(String decision, String rationale) { }
    public record CustomerProfile(Long customerId, String fullName, String status, String cifNo, String phone,
                                  String email, String occupation, String dateOfBirth) { }
    private record Customer(Long customerId, String cifNo, String firstName, String lastName, String dob,
                            String phone, String email, String occupation, String status) { }
    private record Account(String accountId, String accountNumber, String status, String currencyCode, BigDecimal availableBalance) { }
    private record Transaction(String transactionId, String transactionRef, String transactionStatus, BigDecimal amount, String currencyCode, BigDecimal feeAmount, String failureReason) { }
    private record Product(String productCode, String productName, String productTypeCode, BigDecimal minimumBalance, String status, Rate rate) { }
    private record Rate(BigDecimal interestRate) { }

    @ResponseStatus(HttpStatus.BAD_REQUEST) static class AssistantInputException extends RuntimeException { AssistantInputException(String message) { super(message); } }
    @ResponseStatus(HttpStatus.FORBIDDEN) static class AssistantAccessException extends RuntimeException { AssistantAccessException(String message) { super(message); } }
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE) static class AssistantUnavailableException extends RuntimeException { AssistantUnavailableException(String message) { super(message); } }
}
