package com.training.platform.gateway.audit;

import com.training.platform.auditclient.AuditClient;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.core.annotation.Order;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiAccessAuditFilterTest {
    private AuditClient auditClient;
    private JwtDecoder jwtDecoder;
    private ApiAccessAuditFilter filter;

    @BeforeEach
    void setUp() {
        auditClient = mock(AuditClient.class);
        jwtDecoder = mock(JwtDecoder.class);
        filter = new ApiAccessAuditFilter(auditClient, jwtDecoder);
    }

    @Test
    void runsBeforeSpringSecuritySoRejectedRequestsAreAudited() throws Exception {
        Order order = ApiAccessAuditFilter.class.getAnnotation(Order.class);
        assertThat(order.value()).isLessThan(SecurityProperties.DEFAULT_FILTER_ORDER);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/accounts");
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (ignoredRequest, rejectedResponse) ->
                ((MockHttpServletResponse) rejectedResponse).setStatus(401));

        verify(auditClient).record(eq("api-access"), eq("GET_API_REQUEST"), eq("REJECTED"),
                eq("GET /api/accounts completed with HTTP 401"), any(), any(), anyMap());
    }

    @Test
    void recordsVerifiedJwtIdentityAndFinalApiStatus() throws Exception {
        Jwt jwt = mock(Jwt.class);
        when(jwtDecoder.decode("valid-token")).thenReturn(jwt);
        when(jwt.getSubject()).thenReturn("admin");
        when(jwt.getClaim("roles")).thenReturn(List.of("ADMIN"));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/transactions");
        request.addHeader("Authorization", "Bearer valid-token");
        request.addHeader("X-Forwarded-For", "10.20.30.40, 127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (ignoredRequest, successfulResponse) ->
                ((MockHttpServletResponse) successfulResponse).setStatus(201));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> details = ArgumentCaptor.forClass(Map.class);
        verify(auditClient).record(eq("api-access"), eq("POST_API_REQUEST"), eq("SUCCESS"),
                eq("POST /api/transactions completed with HTTP 201"), any(), any(), details.capture());
        assertThat(details.getValue()).containsEntry("username", "admin")
                .containsEntry("actorId", "admin")
                .containsEntry("actorType", "USER")
                .containsEntry("targetService", "transactions-service")
                .containsEntry("clientIp", "10.20.30.40")
                .containsEntry("httpStatus", 201);
    }

    @Test
    void optionsRequestsAreAlsoAudited() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/customers");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (ignoredRequest, successfulResponse) ->
                ((MockHttpServletResponse) successfulResponse).setStatus(204));

        verify(auditClient).record(eq("api-access"), eq("OPTIONS_API_REQUEST"), eq("SUCCESS"),
                eq("OPTIONS /api/customers completed with HTTP 204"), any(), any(), anyMap());
    }
}
