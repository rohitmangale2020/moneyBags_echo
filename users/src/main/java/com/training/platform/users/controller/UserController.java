package com.training.platform.users.controller;

import com.training.platform.users.model.Role;
import com.training.platform.users.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) { this.userService = userService; }

    @GetMapping("/me")
    Map<String, Object> currentUser(Authentication authentication) {
        return Map.of("username", authentication.getName(), "roles", authentication.getAuthorities());
    }

    @PostMapping
    ResponseEntity<Map<String, Object>> createUser(@Valid @RequestBody CreateUserRequest request) {
        try {
            var user = userService.createUser(request.username(), request.password(), request.roles());
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("username", user.getUsername(), "roles", user.getRoles()));
        } catch (UserService.DuplicateUsernameException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    public record CreateUserRequest(@NotBlank String username, @NotBlank String password, Set<Role> roles) { }
}
