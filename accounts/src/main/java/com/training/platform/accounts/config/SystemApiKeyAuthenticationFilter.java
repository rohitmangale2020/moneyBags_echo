package com.training.platform.accounts.config;

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

/** Allows trusted services to post scheduled banking operations without a user JWT. */
@Component
class SystemApiKeyAuthenticationFilter extends OncePerRequestFilter {
    static final String HEADER = "X-Banking-Internal-Key";
    private final byte[] expectedKey;

    SystemApiKeyAuthenticationFilter(@Value("${services.internal-key}") String expectedKey) {
        this.expectedKey = expectedKey.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String supplied = request.getHeader(HEADER);
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
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        "transactions-service", null, List.of(new SimpleGrantedAuthority("ROLE_SYSTEM")));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        filterChain.doFilter(request, response);
    }
}
