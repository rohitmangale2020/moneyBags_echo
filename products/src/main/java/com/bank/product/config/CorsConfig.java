package com.bank.product.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {
    @Value("${app.cors.allowed-origins:http://localhost:8000,http://localhost:8001,http://localhost:8002,http://localhost:8082}")
    private String allowedOrigins;
    @Override public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**").allowedOrigins(allowedOrigins.split(",")).allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE").allowedHeaders("Authorization", "Content-Type");
    }
}
