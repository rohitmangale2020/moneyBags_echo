package com.training.platform.users.config;

import com.training.platform.users.model.User;
import com.training.platform.users.model.UserProfile;
import com.training.platform.users.model.UserStatus;
import com.training.platform.users.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;

@Configuration
public class BootstrapAdminConfiguration {

    @Bean
    ApplicationRunner bootstrapAdmin(UserRepository userRepository, PasswordEncoder passwordEncoder,
                                    @Value("${BOOTSTRAP_ADMIN_USERNAME:}") String username,
                                    @Value("${BOOTSTRAP_ADMIN_PASSWORD:}") String password,
                                    @Value("${BOOTSTRAP_ADMIN_EMAIL:admin@moneybags.local}") String email) {
        return args -> {
            if (!StringUtils.hasText(username) || !StringUtils.hasText(password)
                    || userRepository.existsByUsernameIgnoreCase(username)) {
                return;
            }

            User admin = new User();
            admin.setUsername(username.trim());
            admin.setEmail(email.trim().toLowerCase());
            admin.setPasswordHash(passwordEncoder.encode(password));
            admin.setRole("ADMIN");
            admin.setStatus(UserStatus.ACTIVE);

            UserProfile profile = new UserProfile();
            profile.setFirstName("System");
            profile.setLastName("Administrator");
            profile.setCountryCode("IN");
            admin.attachProfile(profile);
            userRepository.save(admin);
        };
    }
}
