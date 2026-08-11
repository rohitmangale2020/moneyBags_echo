package com.bank.product.config;
import org.springframework.context.annotation.*;
import org.springframework.data.domain.AuditorAware;
import java.util.Optional;
import org.springframework.security.core.context.SecurityContextHolder;
@Configuration public class AuditConfig { @Bean AuditorAware<String> auditorAware() { return () -> Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication()).filter(a -> a.isAuthenticated()).map(a -> a.getName()).or(() -> Optional.of("system")); } }
