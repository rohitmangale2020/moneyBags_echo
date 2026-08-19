package com.training.platform.transactions.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.training.platform.transactions.entity.BankTransaction;
import com.training.platform.transactions.entity.FixedDepositStatus;
import com.training.platform.transactions.entity.TransactionStatus;
import com.training.platform.transactions.entity.TransactionType;
import com.training.platform.transactions.repository.BankTransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Date;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Opt-in Oracle scenario that deliberately leaves all created data in the database.
 *
 * <p>The running services are used to create the customer, accounts, opening deposit,
 * fixed deposit and interest/maturity postings. Direct database access is limited to
 * arranging the requested historical business dates and verifying the committed rows.
 * Run the complete class, in order, and never use it against a production schema.</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "eureka.client.enabled=false"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestMethodOrder(OrderAnnotation.class)
@EnabledIfEnvironmentVariable(named = "RUN_ORACLE_DEPOSIT_E2E", matches = "(?i)true")
class OracleDepositInterestTimelineTest {
    private static final Logger log = LoggerFactory.getLogger(OracleDepositInterestTimelineTest.class);
    private static final LocalDate PAYOUT_DATE = LocalDate.of(2026, 8, 18);
    private static final LocalDate SAVINGS_ACCRUED_THROUGH = PAYOUT_DATE.minusDays(1);
    private static final BigDecimal DAYS_IN_YEAR = new BigDecimal("365");
    private static final Map<String, String> LOCAL_ENV = loadLocalEnvironment();

    @Autowired private JdbcTemplate jdbc;
    @Autowired private BankTransactionRepository transactions;

    private static Scenario scenario;
    private static String accessToken;

    /** Makes an IntelliJ JUnit run behave like the project launcher, which loads the root .env file. */
    @DynamicPropertySource
    static void oracleDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> requiredConfiguration("DBURL"));
        registry.add("spring.datasource.username", () -> requiredConfiguration("DBUSER"));
        registry.add("spring.datasource.password", () -> requiredConfiguration("DBPASSWORD"));
    }

    @Test
    @Order(1)
    void createCustomerSavingsAccountAndFixedDepositDueOn18August2026() {
        RestClient api = api();
        String token = token(api);
        JsonNode products = get(api, token, "/api/v1/products");
        JsonNode savingsProduct = product(products, "SAVINGS", "ORACLE_E2E_SAVINGS_PRODUCT_ID");
        JsonNode fdProduct = product(products, "FD", "ORACLE_E2E_FD_PRODUCT_ID");

        String currency = savingsProduct.path("currency").asText();
        assertEquals(currency, fdProduct.path("currency").asText(),
                "The selected savings and FD products must use the same currency");

        String unique = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        JsonNode customer = post(api, token, "/api/customers", Map.of(
                "firstName", "Vijay",
                "lastName", "Mallya" + unique,
                "dob", "1990-01-15",
                "gender", "OTHER",
                "phone", uniquePhone(),
                "email", "vijay.mallya." + unique + "@loot.lunga",
                "occupation", "Bankruptcy"));
        String customerId = customer.path("customerId").asText();
        assertFalse(customerId.isBlank());

        JsonNode savingsAccount = post(api, token, "/api/accounts",
                accountRequest(customerId, savingsProduct.path("productId").asText(), currency));
        String savingsAccountId = savingsAccount.path("accountId").asText();

        BigDecimal fdMinimum = decimal(fdProduct, "minimumBalance", new BigDecimal("10000.00"));
        BigDecimal savingsMinimum = decimal(savingsProduct, "minimumBalance", BigDecimal.ZERO);
        BigDecimal principal = fdMinimum.max(new BigDecimal("10000.00")).setScale(2, RoundingMode.HALF_EVEN);
        BigDecimal openingAmount = principal.add(savingsMinimum.max(BigDecimal.ZERO))
                .add(new BigDecimal("100000.00"));

        String openingReference = "E2E-OPEN-" + unique;
        JsonNode opening = post(api, token, "/api/transactions",
                openingDeposit(openingReference, customerId, savingsAccountId, openingAmount, currency));
        assertEquals("COMPLETED", opening.path("transactionStatus").asText());

        JsonNode fdAccount = post(api, token, "/api/accounts",
                accountRequest(customerId, fdProduct.path("productId").asText(), currency));
        String fdAccountId = fdAccount.path("accountId").asText();

        JsonNode contract = post(api, token, "/api/fixed-deposits", Map.of(
                "fdAccountId", fdAccountId,
                "fundingAccountId", savingsAccountId,
                "payoutAccountId", savingsAccountId,
                "principal", principal));
        assertEquals("ACTIVE", contract.path("status").asText(),
                "FD funding must complete before its dates are arranged");
        String contractId = contract.path("contractId").asText();
        int tenureMonths = contract.path("tenureMonths").asInt();
        LocalDate fdOpenedOn = PAYOUT_DATE.minusMonths(tenureMonths);

        int accountDatesUpdated = jdbc.update("""
                UPDATE account
                   SET interest_accrued_through = ?,
                       next_interest_payout_date = ?,
                       updated_at = SYSTIMESTAMP,
                       version_no = version_no + 1
                 WHERE account_id = ?
                """, Date.valueOf(SAVINGS_ACCRUED_THROUGH), Date.valueOf(PAYOUT_DATE), savingsAccountId);
        assertEquals(1, accountDatesUpdated);

        int contractDatesUpdated = jdbc.update("""
                UPDATE fixed_deposit_contract
                   SET opened_on = ?,
                       lock_in_until = ADD_MONTHS(?, lock_in_period_months),
                       maturity_date = ?,
                       updated_at = SYSTIMESTAMP,
                       version_no = version_no + 1
                 WHERE contract_id = ?
                   AND contract_status = 'ACTIVE'
                """, Date.valueOf(fdOpenedOn), Date.valueOf(fdOpenedOn),
                Date.valueOf(PAYOUT_DATE), contractId);
        assertEquals(1, contractDatesUpdated);

        BigDecimal savingsBalanceBeforePayout = balance(savingsAccountId);
        BigDecimal savingsRate = accountRate(savingsAccountId);
        BigDecimal fdRate = contractRate(contractId);
        BigDecimal expectedSavingsInterest = interest(savingsBalanceBeforePayout, savingsRate,
                SAVINGS_ACCRUED_THROUGH, PAYOUT_DATE);
        BigDecimal expectedFdInterest = interest(principal, fdRate, fdOpenedOn, PAYOUT_DATE);

        scenario = new Scenario(customerId, savingsAccountId, fdAccountId, contractId,
                principal, savingsBalanceBeforePayout,
                expectedSavingsInterest, expectedFdInterest,
                savingsReference(savingsAccountId), "FD-I-" + compact(contractId),
                "FD-P-" + compact(contractId));

        log.info("Permanent Oracle scenario created: customerId={}, savingsAccountId={}, "
                        + "fdAccountId={}, contractId={}, payoutDate={}",
                customerId, savingsAccountId, fdAccountId, contractId, PAYOUT_DATE);
    }

    @Test
    @Order(2)
    void readCreatedCustomerAccountsAndContractFromOracle() {
        Scenario s = requiredScenario();

        assertEquals(1, count("SELECT COUNT(*) FROM customers WHERE customer_id = ?", s.customerId()));
        assertEquals(1, count("SELECT COUNT(*) FROM account WHERE account_id = ?", s.savingsAccountId()));
        assertEquals(1, count("SELECT COUNT(*) FROM account WHERE account_id = ?", s.fdAccountId()));
        assertEquals(1, count("SELECT COUNT(*) FROM fixed_deposit_contract WHERE contract_id = ?", s.contractId()));

        assertEquals(PAYOUT_DATE, accountDate("next_interest_payout_date", s.savingsAccountId()));
        assertEquals(SAVINGS_ACCRUED_THROUGH,
                accountDate("interest_accrued_through", s.savingsAccountId()));
        assertEquals(PAYOUT_DATE, contractDate("maturity_date", s.contractId()));
        assertEquals("ACTIVE", contractStatus(s.contractId()));
        assertMoney(s.principal(), balance(s.fdAccountId()));
        assertMoney(s.savingsBalanceBeforePayout(), balance(s.savingsAccountId()));

        log.info("Permanent Oracle scenario read successfully: savingsBalance={}, fdPrincipal={}, "
                        + "expectedSavingsInterest={}, expectedFdInterest={}",
                s.savingsBalanceBeforePayout(), s.principal(),
                s.expectedSavingsInterest(), s.expectedFdInterest());
    }

    @Test
    @Order(3)
    void creditsSavingsAndFixedDepositInterestOn18August2026AndKeepsAllRows() {
        Scenario s = requiredScenario();
        RestClient api = api();
        String token = token(api);

        post(api, token, "/api/deposit-processing/interest?asOf=2026-08-17");
        post(api, token, "/api/deposit-processing/maturities?asOf=2026-08-17");
        assertTrue(transactions.findByTransactionRef(s.savingsInterestReference()).isEmpty());
        assertTrue(transactions.findByTransactionRef(s.fdInterestReference()).isEmpty());
        assertTrue(transactions.findByTransactionRef(s.fdPrincipalReference()).isEmpty());

        post(api, token, "/api/deposit-processing/interest?asOf=2026-08-18");
        post(api, token, "/api/deposit-processing/maturities?asOf=2026-08-18");

        BankTransaction savingsInterest = requiredTransaction(s.savingsInterestReference());
        BankTransaction fdInterest = requiredTransaction(s.fdInterestReference());
        BankTransaction fdPrincipal = requiredTransaction(s.fdPrincipalReference());

        assertCompletedCredit(savingsInterest, TransactionType.INTEREST_CREDIT,
                s.savingsAccountId(), s.expectedSavingsInterest());
        assertEquals(PAYOUT_DATE, savingsInterest.getInterestPeriodEnd());

        assertCompletedCredit(fdInterest, TransactionType.FIXED_DEPOSIT_INTEREST_CREDIT,
                s.savingsAccountId(), s.expectedFdInterest());
        assertEquals(PAYOUT_DATE, fdInterest.getInterestPeriodEnd());

        assertEquals(TransactionStatus.COMPLETED, fdPrincipal.getTransactionStatus());
        assertEquals(TransactionType.FIXED_DEPOSIT_MATURITY, fdPrincipal.getTransactionType());
        assertEquals(s.fdAccountId(), fdPrincipal.getDebitAccountId());
        assertEquals(s.savingsAccountId(), fdPrincipal.getCreditAccountId());
        assertMoney(s.principal(), fdPrincipal.getAmount());

        BigDecimal expectedSavingsBalance = s.savingsBalanceBeforePayout()
                .add(s.expectedSavingsInterest())
                .add(s.expectedFdInterest())
                .add(s.principal());
        assertMoney(expectedSavingsBalance, balance(s.savingsAccountId()));
        assertMoney(BigDecimal.ZERO, balance(s.fdAccountId()));
        assertEquals("CLOSED", accountStatus(s.fdAccountId()));
        assertEquals(FixedDepositStatus.MATURED.name(), contractStatus(s.contractId()));
        assertMoney(s.expectedFdInterest(), contractInterestPaid(s.contractId()));

        assertEquals(3, count("""
                SELECT COUNT(*)
                  FROM account_statement statement_entry
                  JOIN bank_transaction transaction
                    ON transaction.transaction_id = statement_entry.transaction_id
                 WHERE transaction.transaction_ref IN (?, ?, ?)
                   AND statement_entry.account_id = ?
                """, s.savingsInterestReference(), s.fdInterestReference(),
                s.fdPrincipalReference(), s.savingsAccountId()));
        assertEquals(3, count("""
                SELECT COUNT(*)
                  FROM transaction_event_outbox outbox_event
                  JOIN bank_transaction transaction
                    ON transaction.transaction_id = outbox_event.aggregate_id
                 WHERE transaction.transaction_ref IN (?, ?, ?)
                """, s.savingsInterestReference(), s.fdInterestReference(), s.fdPrincipalReference()));

        log.info("Permanent Oracle payout verified and retained: customerId={}, savingsAccountId={}, "
                        + "fdAccountId={}, contractId={}, savingsInterestRef={}, fdInterestRef={}, "
                        + "fdPrincipalRef={}, finalSavingsBalance={}",
                s.customerId(), s.savingsAccountId(), s.fdAccountId(), s.contractId(),
                s.savingsInterestReference(), s.fdInterestReference(),
                s.fdPrincipalReference(), expectedSavingsBalance);
    }

    private RestClient api() {
        String baseUrl = environment("BANKING_E2E_BASE_URL", "http://localhost:8080");
        return RestClient.builder().baseUrl(baseUrl).build();
    }

    private String token(RestClient api) {
        if (accessToken != null) return accessToken;
        String suppliedToken = environment("ORACLE_E2E_ACCESS_TOKEN", "");
        if (!suppliedToken.isBlank()) {
            accessToken = suppliedToken.regionMatches(true, 0, "Bearer ", 0, 7)
                    ? suppliedToken.substring(7).trim() : suppliedToken.trim();
            return accessToken;
        }
        String username = environment("ORACLE_E2E_USERNAME",
                environment("BOOTSTRAP_ADMIN_USERNAME", "admin"));
        String password = environment("ORACLE_E2E_PASSWORD",
                environment("BOOTSTRAP_ADMIN_PASSWORD", ""));
        assertFalse(password.isBlank(),
                "Set ORACLE_E2E_PASSWORD (or BOOTSTRAP_ADMIN_PASSWORD) in the test environment");
        JsonNode login;
        try {
            login = api.post().uri("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("username", username, "password", password))
                    .retrieve().body(JsonNode.class);
        } catch (HttpClientErrorException.Unauthorized exception) {
            throw new AssertionError("Login failed for '" + username + "'. ORACLE_E2E_PASSWORD must be "
                    + "that existing user's current application password; BOOTSTRAP_ADMIN_PASSWORD does not "
                    + "reset an admin that is already stored. Alternatively set ORACLE_E2E_ACCESS_TOKEN.", exception);
        }
        assertNotNull(login);
        accessToken = login.path("accessToken").asText();
        assertFalse(accessToken.isBlank(), "Login response did not contain an access token");
        return accessToken;
    }

    private JsonNode get(RestClient api, String token, String path) {
        JsonNode response = api.get().uri(path).headers(headers -> headers.setBearerAuth(token))
                .retrieve().body(JsonNode.class);
        assertNotNull(response);
        return response;
    }

    private JsonNode post(RestClient api, String token, String path, Object body) {
        JsonNode response = api.post().uri(path).headers(headers -> headers.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON).body(body)
                .retrieve().body(JsonNode.class);
        assertNotNull(response);
        return response;
    }

    private JsonNode post(RestClient api, String token, String path) {
        JsonNode response;
        try {
            response = api.post().uri(path).headers(headers -> headers.setBearerAuth(token))
                    .retrieve().body(JsonNode.class);
        } catch (HttpClientErrorException.Forbidden exception) {
            throw new AssertionError("Access was forbidden for " + path
                    + ". Deposit processing requires an ADMIN token. Remove a stale employee "
                    + "ORACLE_E2E_ACCESS_TOKEN or replace it with a token whose roles claim contains ADMIN.",
                    exception);
        }
        assertNotNull(response);
        return response;
    }

    private JsonNode product(JsonNode products, String type, String preferredIdEnvironmentName) {
        String preferredId = environment(preferredIdEnvironmentName, "");
        for (JsonNode product : products) {
            if (!"ACTIVE".equalsIgnoreCase(product.path("status").asText())) continue;
            if (!type.equalsIgnoreCase(product.path("productTypeCode").asText())) continue;
            if (!preferredId.isBlank() && !preferredId.equals(product.path("productId").asText())) continue;
            BigDecimal rate = product.path("rate").path("interestRate").decimalValue();
            if (rate.signum() <= 0) continue;
            if ("FD".equals(type) && product.path("term").path("tenureMonths").asInt() <= 0) continue;
            return product;
        }
        throw new AssertionError("No suitable active " + type + " product was returned by the products API"
                + (preferredId.isBlank() ? "" : " for product ID " + preferredId));
    }

    private Map<String, Object> accountRequest(String customerId, String productId, String currency) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("customerId", customerId);
        request.put("productId", productId);
        request.put("ownershipType", "INDIVIDUAL");
        request.put("status", "ACTIVE");
        request.put("currencyCode", currency);
        request.put("availableBalance", BigDecimal.ZERO);
        return request;
    }

    private Map<String, Object> openingDeposit(String reference, String customerId, String accountId,
                                               BigDecimal amount, String currency) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("transactionRef", reference);
        request.put("transactionType", "OPENING_DEPOSIT");
        request.put("transactionStatus", "INITIATED");
        request.put("creditAccountId", accountId);
        request.put("amount", amount);
        request.put("currencyCode", currency);
        request.put("feeAmount", BigDecimal.ZERO);
        request.put("initiatedByCustomerId", customerId);
        return request;
    }

    private void assertCompletedCredit(BankTransaction transaction, TransactionType type,
                                       String accountId, BigDecimal amount) {
        assertEquals(TransactionStatus.COMPLETED, transaction.getTransactionStatus());
        assertEquals(type, transaction.getTransactionType());
        assertEquals(accountId, transaction.getCreditAccountId());
        assertEquals(null, transaction.getDebitAccountId());
        assertMoney(amount, transaction.getAmount());
        assertNotNull(transaction.getCompletedAt());
    }

    private BankTransaction requiredTransaction(String reference) {
        return transactions.findByTransactionRef(reference)
                .orElseThrow(() -> new AssertionError("Expected transaction was not created: " + reference));
    }

    private Scenario requiredScenario() {
        if (scenario == null) scenario = loadLatestScenarioFromOracle();
        return scenario;
    }

    private Scenario loadLatestScenarioFromOracle() {
        return jdbc.query("""
                        SELECT contract.contract_id,
                               contract.customer_id,
                               contract.fd_account_id,
                               contract.funding_account_id,
                               contract.principal,
                               contract.annual_interest_rate AS fd_rate,
                               contract.opened_on,
                               contract.maturity_date,
                               savings.available_balance AS savings_balance,
                               savings.annual_interest_rate AS savings_rate,
                               savings.interest_accrued_through
                          FROM fixed_deposit_contract contract
                          JOIN account savings
                            ON savings.account_id = contract.funding_account_id
                          JOIN customers customer
                            ON TO_CHAR(customer.customer_id) = contract.customer_id
                         WHERE customer.email LIKE 'oracle.interest.%@example.test'
                           AND contract.maturity_date = ?
                         ORDER BY contract.created_at DESC
                         FETCH FIRST 1 ROW ONLY
                        """,
                (resultSet, rowNumber) -> {
                    String contractId = resultSet.getString("contract_id");
                    String savingsAccountId = resultSet.getString("funding_account_id");
                    BigDecimal principal = resultSet.getBigDecimal("principal");
                    BigDecimal savingsBalance = resultSet.getBigDecimal("savings_balance");
                    LocalDate accruedThrough = resultSet.getDate("interest_accrued_through").toLocalDate();
                    LocalDate maturityDate = resultSet.getDate("maturity_date").toLocalDate();
                    BigDecimal expectedSavingsInterest = interest(savingsBalance,
                            resultSet.getBigDecimal("savings_rate"), accruedThrough, PAYOUT_DATE);
                    BigDecimal expectedFdInterest = interest(principal,
                            resultSet.getBigDecimal("fd_rate"),
                            resultSet.getDate("opened_on").toLocalDate(), maturityDate);
                    return new Scenario(resultSet.getString("customer_id"), savingsAccountId,
                            resultSet.getString("fd_account_id"), contractId,
                            principal, savingsBalance, expectedSavingsInterest, expectedFdInterest,
                            savingsReference(savingsAccountId), "FD-I-" + compact(contractId),
                            "FD-P-" + compact(contractId));
                }, Date.valueOf(PAYOUT_DATE)).stream().findFirst()
                .orElseThrow(() -> new AssertionError(
                        "No permanent Oracle interest scenario was found. Run the create test first."));
    }

    private BigDecimal balance(String accountId) {
        return jdbc.queryForObject("SELECT available_balance FROM account WHERE account_id = ?",
                BigDecimal.class, accountId);
    }

    private BigDecimal accountRate(String accountId) {
        return jdbc.queryForObject("SELECT annual_interest_rate FROM account WHERE account_id = ?",
                BigDecimal.class, accountId);
    }

    private BigDecimal contractRate(String contractId) {
        return jdbc.queryForObject("SELECT annual_interest_rate FROM fixed_deposit_contract WHERE contract_id = ?",
                BigDecimal.class, contractId);
    }

    private BigDecimal contractInterestPaid(String contractId) {
        return jdbc.queryForObject("SELECT interest_paid FROM fixed_deposit_contract WHERE contract_id = ?",
                BigDecimal.class, contractId);
    }

    private LocalDate accountDate(String column, String accountId) {
        if (!column.equals("next_interest_payout_date") && !column.equals("interest_accrued_through")) {
            throw new IllegalArgumentException("Unsupported account date column");
        }
        Date date = jdbc.queryForObject("SELECT " + column + " FROM account WHERE account_id = ?",
                Date.class, accountId);
        return date.toLocalDate();
    }

    private LocalDate contractDate(String column, String contractId) {
        if (!column.equals("maturity_date")) throw new IllegalArgumentException("Unsupported contract date column");
        Date date = jdbc.queryForObject("SELECT " + column
                + " FROM fixed_deposit_contract WHERE contract_id = ?", Date.class, contractId);
        return date.toLocalDate();
    }

    private String accountStatus(String accountId) {
        return jdbc.queryForObject("SELECT status FROM account WHERE account_id = ?", String.class, accountId);
    }

    private String contractStatus(String contractId) {
        return jdbc.queryForObject("SELECT contract_status FROM fixed_deposit_contract WHERE contract_id = ?",
                String.class, contractId);
    }

    private int count(String sql, Object... arguments) {
        Number value = jdbc.queryForObject(sql, Number.class, arguments);
        return value.intValue();
    }

    private BigDecimal decimal(JsonNode node, String field, BigDecimal fallback) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? fallback : value.decimalValue();
    }

    private BigDecimal interest(BigDecimal amount, BigDecimal annualRate,
                                LocalDate accruedThrough, LocalDate periodEnd) {
        long days = Math.max(0, ChronoUnit.DAYS.between(accruedThrough, periodEnd));
        return amount.multiply(annualRate).multiply(BigDecimal.valueOf(days))
                .divide(new BigDecimal("100"), 12, RoundingMode.HALF_EVEN)
                .divide(DAYS_IN_YEAR, 2, RoundingMode.HALF_EVEN);
    }

    private void assertMoney(BigDecimal expected, BigDecimal actual) {
        assertNotNull(actual);
        assertEquals(0, expected.compareTo(actual),
                () -> "Expected monetary value " + expected + " but was " + actual);
    }

    private String savingsReference(String accountId) {
        String compactId = compact(accountId);
        compactId = compactId.substring(0, Math.min(32, compactId.length()));
        return "SI2608-" + compactId;
    }

    private String compact(String id) {
        return id.replace("-", "");
    }

    private String uniquePhone() {
        long suffix = Math.floorMod(System.nanoTime(), 1_000_000_000L);
        return "9" + String.format("%09d", suffix);
    }

    private String environment(String name, String fallback) {
        String value = configuredValue(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String requiredConfiguration(String name) {
        String value = configuredValue(name);
        if (value == null || value.isBlank() || value.equals("${" + name + "}")) {
            throw new IllegalStateException("Missing " + name
                    + ". Set it in the IntelliJ JUnit environment or create and complete the project root .env file");
        }
        return value;
    }

    private static String configuredValue(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) value = System.getProperty(name);
        if (value == null || value.isBlank()) value = LOCAL_ENV.get(name);
        if (value == null) return null;
        value = value.trim();
        if (value.length() >= 2
                && ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'")))) {
            value = value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static Map<String, String> loadLocalEnvironment() {
        Path directory = Path.of("").toAbsolutePath();
        for (int depth = 0; depth < 4 && directory != null; depth++, directory = directory.getParent()) {
            Path candidate = directory.resolve(".env");
            if (!Files.isRegularFile(candidate)) continue;
            try {
                Map<String, String> values = new HashMap<>();
                for (String rawLine : Files.readAllLines(candidate)) {
                    String line = rawLine.trim();
                    if (line.isBlank() || line.startsWith("#")) continue;
                    if (line.startsWith("export ")) line = line.substring(7).trim();
                    int separator = line.indexOf('=');
                    if (separator <= 0) continue;
                    String key = line.substring(0, separator).trim();
                    String value = line.substring(separator + 1).trim();
                    if (value.length() >= 2
                            && ((value.startsWith("\"") && value.endsWith("\""))
                            || (value.startsWith("'") && value.endsWith("'")))) {
                        value = value.substring(1, value.length() - 1);
                    }
                    values.put(key, value);
                }
                return Map.copyOf(values);
            } catch (java.io.IOException exception) {
                throw new IllegalStateException("Could not read " + candidate, exception);
            }
        }
        return Map.of();
    }

    private record Scenario(
            String customerId,
            String savingsAccountId,
            String fdAccountId,
            String contractId,
            BigDecimal principal,
            BigDecimal savingsBalanceBeforePayout,
            BigDecimal expectedSavingsInterest,
            BigDecimal expectedFdInterest,
            String savingsInterestReference,
            String fdInterestReference,
            String fdPrincipalReference) { }
}
