package com.training.platform.audit.config;

import com.training.platform.auditclient.AuditClient;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Protects append endpoints independently of end-user JWT authentication. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class InternalAuditKeyFilter extends OncePerRequestFilter {
    private final byte[] expectedKey;

    public InternalAuditKeyFilter(@Value("${audit.internal-key:local-audit-key}") String expectedKey) {
        this.expectedKey = expectedKey.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !HttpMethod.POST.matches(request.getMethod())
                || !request.getRequestURI().startsWith("/api/audit/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String suppliedKey = request.getHeader(AuditClient.INTERNAL_KEY_HEADER);
        byte[] suppliedBytes = suppliedKey == null
                ? new byte[0] : suppliedKey.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expectedKey, suppliedBytes)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\":\"Invalid internal audit key\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
