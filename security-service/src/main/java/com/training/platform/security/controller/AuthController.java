package com.training.platform.security.controller;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.training.platform.auditclient.AuditClient;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@RestController
@RequestMapping("/auth")
class AuthController {
    private final RestClient usersClient;
    private final JwtEncoder jwtEncoder;
    private final RSAKey rsaKey;
    private final String issuer;
    private final String audience;
    private final Duration ttl;
    private final AuditClient auditClient;

    AuthController(RestClient.Builder restClientBuilder, JwtEncoder jwtEncoder, RSAKey rsaKey,
                   @Value("${security-service.users-url}") String usersUrl,
                   @Value("${security-service.issuer}") String issuer,
                   @Value("${security-service.audience}") String audience,
                   @Value("${security-service.access-token-ttl}") Duration ttl,
                   AuditClient auditClient) {
        this.usersClient = restClientBuilder.baseUrl(usersUrl).build();
        this.jwtEncoder = jwtEncoder;
        this.rsaKey = rsaKey;
        this.issuer = issuer;
        this.audience = audience;
        this.ttl = ttl;
        this.auditClient = auditClient;
    }

    @PostMapping("/login")
    ResponseEntity<TokenResponse> login(@Valid @RequestBody Credentials credentials) {
        UserResponse user;
        try {
            user = usersClient.post().uri("/internal/users/authenticate").body(credentials).retrieve().body(UserResponse.class);
        } catch (RestClientResponseException exception) {
            auditClient.rejected("security", "LOGIN_FAILED", "Login credentials were rejected",
                    "INVALID_CREDENTIALS", "Invalid username or password",
                    loginDetails(credentials.username()));
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (user == null) {
            auditClient.rejected("security", "LOGIN_FAILED", "Login returned no user",
                    "INVALID_CREDENTIALS", "Invalid username or password",
                    loginDetails(credentials.username()));
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(issuer).subject(user.username()).audience(List.of(audience))
                .issuedAt(now).expiresAt(now.plus(ttl)).claim("roles", user.roles())
                .claim("userId", user.userId()).build();
        String token = jwtEncoder.encode(org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).keyId(rsaKey.getKeyID()).build(), claims)).getTokenValue();
        Map<String, Object> details = new java.util.LinkedHashMap<>();
        details.put("userId", user.userId());
        details.put("username", user.username());
        details.put("clientIp", auditClient.currentClientIp());
        details.put("actorId", user.userId().toString());
        details.put("actorType", "USER");
        auditClient.success("security", "LOGIN_SUCCEEDED", "User logged in successfully", details);
        return ResponseEntity.ok(new TokenResponse(token, "Bearer", ttl.toSeconds()));
    }

    @GetMapping("/jwks")
    Map<String, Object> jwks() { return new JWKSet(rsaKey.toPublicJWK()).toJSONObject(); }

    private Map<String, Object> loginDetails(String username) {
        Map<String, Object> details = new java.util.LinkedHashMap<>();
        details.put("username", username);
        details.put("clientIp", auditClient.currentClientIp());
        return details;
    }

    record Credentials(@NotBlank String username, @NotBlank String password) { }
    record UserResponse(Long userId, String username, java.util.Set<String> roles) { }
    record TokenResponse(String accessToken, String tokenType, long expiresIn) { }
}
