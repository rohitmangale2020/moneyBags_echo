package com.training.platform.customers.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public final class CurrentUser {

    private CurrentUser() {
    }

    public static String id() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            throw new IllegalStateException("An authenticated JWT user is required");
        }
        Number userId = jwtAuthentication.getToken().getClaim("userId");
        if (userId == null) {
            throw new IllegalStateException("JWT does not contain a userId claim");
        }
        return userId.longValue() + "";
    }
}