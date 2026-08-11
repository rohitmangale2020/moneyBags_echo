package com.bank.product.config;
import org.springframework.context.annotation.*;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
@Configuration @EnableMethodSecurity public class SecurityConfig {
 @Bean SecurityFilterChain filterChain(HttpSecurity http) throws Exception { return http.csrf(c -> c.disable()).cors(Customizer.withDefaults()).authorizeHttpRequests(a -> a.requestMatchers("/actuator/health").permitAll().anyRequest().authenticated()).httpBasic(Customizer.withDefaults()).build(); }
 @Bean UserDetailsService users(PasswordEncoder encoder) { return new InMemoryUserDetailsManager(User.withUsername("admin").password(encoder.encode("changeit-admin")).roles("ADMIN").build(), User.withUsername("employee").password(encoder.encode("changeit-employee")).roles("EMPLOYEE").build(), User.withUsername("user").password(encoder.encode("changeit-user")).roles("USER").build()); }
 @Bean PasswordEncoder passwordEncoder() { return PasswordEncoderFactories.createDelegatingPasswordEncoder(); }
}
