package com.training.platform.users.controller;

import com.training.platform.users.model.User;
import com.training.platform.users.model.UserStatus;
import com.training.platform.users.repository.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/users")
public class UserAuthenticationController {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserAuthenticationController(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/authenticate")
    ResponseEntity<UserResponse> authenticate(@Valid @RequestBody Credentials request) {
        return userRepository.findByUsernameIgnoreCase(request.username().trim())
                .filter(user -> user.getStatus() != UserStatus.LOCKED && user.getStatus() != UserStatus.DEACTIVATED)
                .filter(user -> passwordEncoder.matches(request.password(), user.getPasswordHash()))
                .map(user -> ResponseEntity.ok(UserResponse.from(user)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    public record Credentials(@NotBlank String username, @NotBlank String password) { }
    public record UserResponse(Long userId, String username, Set<String> roles, boolean passwordChangeRequired) {
        static UserResponse from(User user) {
            return new UserResponse(user.getId(), user.getUsername(), Set.of(user.getRole()), user.isPasswordChangeRequired());
        }
    }
}
