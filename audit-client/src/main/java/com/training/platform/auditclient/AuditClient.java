package com.training.platform.auditclient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Best-effort synchronous client used by banking services to append audit rows. */
public class AuditClient {
    public static final String CORRELATION_HEADER = "X-Correlation-ID";
    public static final String INTERNAL_KEY_HEADER = "X-Audit-Service-Key";
    private static final String CORRELATION_ATTRIBUTE = AuditClient.class.getName() + ".correlationId";
    private static final Logger log = LoggerFactory.getLogger(AuditClient.class);

    private final RestClient restClient;
    private final String internalKey;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    AuditClient(String baseUrl, String internalKey) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(3));
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
        this.internalKey = internalKey;
    }

    public void success(String category, String action, String description, Map<String, ?> details) {
        record(category, action, "SUCCESS", description, null, null, details);
    }

    public void failed(String category, String action, String description, String errorCode,
                       String errorMessage, Map<String, ?> details) {
        record(category, action, "FAILED", description, errorCode, errorMessage, details);
    }

    public void rejected(String category, String action, String description, String errorCode,
                         String errorMessage, Map<String, ?> details) {
        record(category, action, "REJECTED", description, errorCode, errorMessage, details);
    }

    public void record(String category, String action, String outcome, String description,
                       String errorCode, String errorMessage, Map<String, ?> details) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("auditId", UUID.randomUUID().toString());
        payload.put("correlationId", currentCorrelationId());
        payload.put("action", limit(action, 100));
        payload.put("actorId", currentActorId());
        payload.put("actorType", currentActorType());
        payload.put("outcome", outcome);
        payload.put("description", limit(description, 500));
        payload.put("errorCode", errorCode);
        payload.put("errorMessage", limit(errorMessage, 1000));
        if (details != null) {
            payload.putAll(details);
        }

        Runnable sender = () -> send(category, action, payload);
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sender.run();
                }
            });
        } else {
            sender.run();
        }
    }

    private void send(String category, String action, Map<String, Object> payload) {
        RestClientException lastFailure = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                restClient.post()
                        .uri("/api/audit/{category}", category)
                        .header(INTERNAL_KEY_HEADER, internalKey)
                        .header(CORRELATION_HEADER, payload.get("correlationId").toString())
                        .body(payload)
                        .retrieve()
                        .toBodilessEntity();
                return;
            } catch (RestClientResponseException exception) {
                lastFailure = exception;
                if (!exception.getStatusCode().is5xxServerError()) {
                    logDeliveryFailure(category, action, payload, exception, attempt);
                    return;
                }
            } catch (RestClientException exception) {
                lastFailure = exception;
            }
        }
        if (lastFailure != null) {
            logDeliveryFailure(category, action, payload, lastFailure, 3);
        }
    }

    private void logDeliveryFailure(String category, String action, Map<String, Object> payload,
                                    RestClientException exception, int attempts) {
        if (exception instanceof RestClientResponseException responseException) {
            log.warn("Audit event rejected category={} action={} auditId={} correlationId={} status={} attempts={} body={}",
                    category, action, payload.get("auditId"), payload.get("correlationId"),
                    responseException.getStatusCode(), attempts,
                    limit(responseException.getResponseBodyAsString(), 1000));
            return;
        }
        log.warn("Audit event could not be stored category={} action={} auditId={} correlationId={} attempts={}: {}",
                category, action, payload.get("auditId"), payload.get("correlationId"), attempts,
                exception.getMessage());
    }

    public String currentCorrelationId() {
        HttpServletRequest request = currentRequest();
        if (request != null) {
            Object existing = request.getAttribute(CORRELATION_ATTRIBUTE);
            if (existing instanceof String value) {
                return value;
            }
            String value = validCorrelationId(request.getHeader(CORRELATION_HEADER));
            request.setAttribute(CORRELATION_ATTRIBUTE, value);
            return value;
        }
        return UUID.randomUUID().toString();
    }

    public String currentClientIp() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return limit(forwarded.split(",")[0].trim(), 45);
        }
        return limit(request.getRemoteAddr(), 45);
    }

    public String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authenticated(authentication) ? limit(authentication.getName(), 100) : null;
    }

    /** Returns common audit columns containing only fields whose values differ. */
    public Map<String, Object> changes(Map<String, ?> previousValues, Map<String, ?> newValues) {
        Map<String, ?> previous = previousValues == null ? Map.of() : previousValues;
        Map<String, ?> current = newValues == null ? Map.of() : newValues;
        Set<String> fields = new LinkedHashSet<>();
        fields.addAll(previous.keySet());
        fields.addAll(current.keySet());

        Map<String, Object> changedPrevious = new LinkedHashMap<>();
        Map<String, Object> changedCurrent = new LinkedHashMap<>();
        for (String field : fields) {
            Object oldValue = previous.get(field);
            Object newValue = current.get(field);
            if (!sameValue(oldValue, newValue)) {
                changedPrevious.put(field, oldValue);
                changedCurrent.put(field, newValue);
            }
        }
        if (changedCurrent.isEmpty()) {
            return Map.of();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("changedFields", limit(String.join(",", changedCurrent.keySet()), 500));
        result.put("oldValuesJson", json(changedPrevious));
        result.put("newValuesJson", json(changedCurrent));
        return result;
    }

    private String currentActorId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authenticated(authentication) ? limit(authentication.getName(), 100) : null;
    }

    private String currentActorType() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!authenticated(authentication)) {
            return "ANONYMOUS";
        }
        boolean customer = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_CUSTOMER".equals(authority.getAuthority()));
        return customer ? "CUSTOMER" : "USER";
    }

    private boolean authenticated(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    private boolean sameValue(Object oldValue, Object newValue) {
        if (oldValue instanceof BigDecimal oldNumber && newValue instanceof BigDecimal newNumber) {
            return oldNumber.compareTo(newNumber) == 0;
        }
        return Objects.deepEquals(oldValue, newValue);
    }

    private String json(Map<String, Object> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException exception) {
            log.warn("Audit old/new values could not be serialized: {}", exception.getMessage());
            return "{}";
        }
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private String validCorrelationId(String value) {
        if (value == null || value.isBlank() || value.length() > 36) {
            return UUID.randomUUID().toString();
        }
        return value;
    }

    private String limit(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
