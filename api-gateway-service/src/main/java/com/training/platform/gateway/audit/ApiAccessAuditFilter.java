package com.training.platform.gateway.audit;

import com.training.platform.auditclient.AuditClient;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.core.annotation.Order;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Adds a correlation ID and records the final HTTP outcome of each routed API call. */
@Component
@Order(SecurityProperties.DEFAULT_FILTER_ORDER - 1)
public class ApiAccessAuditFilter extends OncePerRequestFilter {
    private final AuditClient auditClient;
    private final JwtDecoder jwtDecoder;

    public ApiAccessAuditFilter(AuditClient auditClient, JwtDecoder jwtDecoder) {
        this.auditClient = auditClient;
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator") || path.startsWith("/eureka")
                || path.startsWith("/favicon");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String correlationId = correlationId(request.getHeader(AuditClient.CORRELATION_HEADER));
        AuditIdentity identity = identity(request);
        HttpServletRequest wrappedRequest = new CorrelationRequestWrapper(request, correlationId);
        response.setHeader(AuditClient.CORRELATION_HEADER, correlationId);
        long startedAt = System.nanoTime();
        Exception failure = null;

        try {
            filterChain.doFilter(wrappedRequest, response);
        } catch (IOException | ServletException | RuntimeException exception) {
            failure = exception;
            throw exception;
        } finally {
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000L;
            int status = failure == null ? response.getStatus() : Math.max(500, response.getStatus());
            String outcome = status >= 500 ? "FAILED" : status >= 400 ? "REJECTED" : "SUCCESS";
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("correlationId", correlationId);
            details.put("username", identity.username());
            if (identity.username() != null) {
                details.put("actorId", identity.username());
                details.put("actorType", identity.customer() ? "CUSTOMER" : "USER");
            }
            details.put("targetService", targetService(request.getRequestURI()));
            details.put("httpMethod", request.getMethod());
            details.put("requestPath", request.getRequestURI());
            details.put("httpStatus", status);
            details.put("clientIp", clientIp(request));
            details.put("durationMs", durationMs);
            auditClient.record("api-access", request.getMethod().toUpperCase() + "_API_REQUEST", outcome,
                    request.getMethod() + " " + request.getRequestURI() + " completed with HTTP " + status,
                    failure == null ? null : "GATEWAY_REQUEST_FAILED",
                    failure == null ? null : failure.getMessage(), details);
        }
    }

    private AuditIdentity identity(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return AuditIdentity.anonymous();
        }
        String token = authorization.substring(7).trim();
        if (token.isEmpty()) return AuditIdentity.anonymous();
        try {
            Jwt jwt = jwtDecoder.decode(token);
            Object rolesClaim = jwt.getClaim("roles");
            boolean customer = rolesClaim instanceof Iterable<?> roles
                    && containsCustomerRole(roles);
            return new AuditIdentity(jwt.getSubject(), customer);
        } catch (RuntimeException ignored) {
            return AuditIdentity.anonymous();
        }
    }

    private boolean containsCustomerRole(Iterable<?> roles) {
        for (Object role : roles) {
            if ("CUSTOMER".equalsIgnoreCase(String.valueOf(role))
                    || "ROLE_CUSTOMER".equalsIgnoreCase(String.valueOf(role))) {
                return true;
            }
        }
        return false;
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        String value = forwarded == null || forwarded.isBlank()
                ? request.getRemoteAddr() : forwarded.split(",")[0].trim();
        if (value == null) return null;
        return value.length() <= 45 ? value : value.substring(0, 45);
    }

    private String correlationId(String supplied) {
        return supplied == null || supplied.isBlank() || supplied.length() > 36
                ? UUID.randomUUID().toString() : supplied;
    }

    private String targetService(String path) {
        if (path.startsWith("/auth") || path.startsWith("/.well-known")) return "security-service";
        if (path.startsWith("/api/v1/users")) return "users-service";
        if (path.startsWith("/api/customers")) return "customers-service";
        if (path.startsWith("/api/v1/products") || path.startsWith("/api/v1/product-types")) {
            return "products-service";
        }
        if (path.startsWith("/api/accounts")) return "accounts-service";
        if (path.startsWith("/api/transactions") || path.startsWith("/api/statements")) {
            return "transactions-service";
        }
        if (path.startsWith("/api/audit")) return "audit-service";
        return "unknown-service";
    }

    private static final class CorrelationRequestWrapper extends HttpServletRequestWrapper {
        private final String correlationId;

        private CorrelationRequestWrapper(HttpServletRequest request, String correlationId) {
            super(request);
            this.correlationId = correlationId;
        }

        @Override
        public String getHeader(String name) {
            if (AuditClient.CORRELATION_HEADER.equalsIgnoreCase(name)) {
                return correlationId;
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if (AuditClient.CORRELATION_HEADER.equalsIgnoreCase(name)) {
                return Collections.enumeration(Collections.singleton(correlationId));
            }
            return super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            Set<String> names = new LinkedHashSet<>();
            Enumeration<String> original = super.getHeaderNames();
            if (original != null) {
                original.asIterator().forEachRemaining(names::add);
            }
            names.add(AuditClient.CORRELATION_HEADER);
            return Collections.enumeration(names);
        }
    }

    private record AuditIdentity(String username, boolean customer) {
        private static AuditIdentity anonymous() {
            return new AuditIdentity(null, false);
        }
    }
}
