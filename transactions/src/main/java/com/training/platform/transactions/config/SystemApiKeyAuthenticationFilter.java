package com.training.platform.transactions.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Authenticates trusted banking service-to-service calls without a user token. */
@Component
class SystemApiKeyAuthenticationFilter extends OncePerRequestFilter {
    private final byte[] expectedKey;

    SystemApiKeyAuthenticationFilter(@Value("${services.accounts.internal-key}") String expectedKey) {
        this.expectedKey = expectedKey.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String supplied = request.getHeader("X-Banking-Internal-Key");
        if (supplied != null && MessageDigest.isEqual(expectedKey,
                supplied.getBytes(StandardCharsets.UTF_8))) {
            Authentication current = SecurityContextHolder.getContext().getAuthentication();
            if (current instanceof JwtAuthenticationToken jwtAuthentication) {
                List<GrantedAuthority> authorities = new ArrayList<>(jwtAuthentication.getAuthorities());
                if (authorities.stream().noneMatch(authority -> "ROLE_SYSTEM".equals(authority.getAuthority()))) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_SYSTEM"));
                }
                JwtAuthenticationToken trusted = new JwtAuthenticationToken(jwtAuthentication.getToken(),
                        authorities, jwtAuthentication.getName());
                trusted.setDetails(jwtAuthentication.getDetails());
                SecurityContextHolder.getContext().setAuthentication(trusted);
            } else {
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken("accounts-service", null,
                                List.of(new SimpleGrantedAuthority("ROLE_SYSTEM"))));
            }
        }
        filterChain.doFilter(request, response);
    }
}
