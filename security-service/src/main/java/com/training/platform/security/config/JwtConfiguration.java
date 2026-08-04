package com.training.platform.security.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration
public class JwtConfiguration {
    @Bean
    RSAKey rsaKey(@Value("${security-service.jwt-private-key:}") String privateKey,
                  @Value("${security-service.jwt-public-key:}") String publicKey,
                  @Value("${security-service.key-id}") String keyId) throws Exception {
        if (!privateKey.isBlank() && !publicKey.isBlank()) {
            KeyFactory factory = KeyFactory.getInstance("RSA");
            RSAPrivateKey parsedPrivateKey = (RSAPrivateKey) factory.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKey)));
            RSAPublicKey parsedPublicKey = (RSAPublicKey) factory.generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(publicKey)));
            return new RSAKey.Builder(parsedPublicKey).privateKey(parsedPrivateKey).keyID(keyId).build();
        }
        return new RSAKeyGenerator(2048).keyID(keyId).generate();
    }

    @Bean
    JwtEncoder jwtEncoder(RSAKey rsaKey) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(rsaKey)));
    }
}
