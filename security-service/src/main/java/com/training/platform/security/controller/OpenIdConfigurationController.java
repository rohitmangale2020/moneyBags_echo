package com.training.platform.security.controller;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OpenIdConfigurationController {
    private final String issuer;
    public OpenIdConfigurationController(@Value("${security-service.issuer}") String issuer) { this.issuer = issuer; }
    @GetMapping("/.well-known/openid-configuration")
    Map<String, String> openIdConfiguration() { return Map.of("issuer", issuer, "jwks_uri", issuer + "/auth/jwks"); }
}
