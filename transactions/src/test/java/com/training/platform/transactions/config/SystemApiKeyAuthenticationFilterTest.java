package com.training.platform.transactions.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class SystemApiKeyAuthenticationFilterTest {
    private static final String KEY = "test-banking-key";

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validInternalKeyAddsSystemAuthorityWithoutLosingUserIdentity() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(employee("riya_patil"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Banking-Internal-Key", KEY);

        new SystemApiKeyAuthenticationFilter(KEY).doFilter(request, new MockHttpServletResponse(),
                (ignoredRequest, ignoredResponse) -> { });

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertInstanceOf(JwtAuthenticationToken.class, authentication);
        assertEquals("riya_patil", authentication.getName());
        assertTrue(hasAuthority(authentication, "ROLE_EMPLOYEE"));
        assertTrue(hasAuthority(authentication, "ROLE_SYSTEM"));
    }

    @Test
    void validInternalKeyAuthenticatesScheduledServiceCallWithoutJwt() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Banking-Internal-Key", KEY);

        new SystemApiKeyAuthenticationFilter(KEY).doFilter(request, new MockHttpServletResponse(),
                (ignoredRequest, ignoredResponse) -> { });

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertEquals("accounts-service", authentication.getName());
        assertTrue(hasAuthority(authentication, "ROLE_SYSTEM"));
    }

    @Test
    void invalidInternalKeyDoesNotElevateEmployee() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(employee("riya_patil"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Banking-Internal-Key", "wrong-key");

        new SystemApiKeyAuthenticationFilter(KEY).doFilter(request, new MockHttpServletResponse(),
                (ignoredRequest, ignoredResponse) -> { });

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertTrue(hasAuthority(authentication, "ROLE_EMPLOYEE"));
        org.junit.jupiter.api.Assertions.assertFalse(hasAuthority(authentication, "ROLE_SYSTEM"));
    }

    private JwtAuthenticationToken employee(String username) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(username)
                .claim("roles", List.of("EMPLOYEE"))
                .build();
        return new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_EMPLOYEE")), username);
    }

    private boolean hasAuthority(Authentication authentication, String authority) {
        return authentication.getAuthorities().stream()
                .anyMatch(candidate -> authority.equals(candidate.getAuthority()));
    }
}
