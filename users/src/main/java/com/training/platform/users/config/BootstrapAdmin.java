package com.training.platform.users.config;

import com.training.platform.users.repository.UserRepository;
import com.training.platform.users.model.Role;
import com.training.platform.users.service.UserService;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BootstrapAdmin {
    @Bean
    CommandLineRunner createConfiguredAdmin(UserRepository users, UserService userService,
                                           @Value("${app.bootstrap.admin-username:}") String username,
                                           @Value("${app.bootstrap.admin-password:}") String password) {
        return arguments -> {
            if (!username.isBlank() && !password.isBlank() && users.findByUsername(username).isEmpty()) {
                userService.createUser(username, password, Set.of(Role.ADMIN));
            }
        };
    }
}
