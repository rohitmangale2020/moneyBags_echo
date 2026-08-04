package com.training.platform.users.controller;

import com.training.platform.users.model.UserAccount;
import com.training.platform.users.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/users")
public class UserAuthenticationController {
    private final UserService userService;

    public UserAuthenticationController(UserService userService) { this.userService = userService; }

    @PostMapping("/authenticate")
    ResponseEntity<UserResponse> authenticate(@Valid @RequestBody Credentials request) {
        try {
            return ResponseEntity.ok(UserResponse.from(userService.authenticate(request.username(), request.password())));
        } catch (UserService.InvalidCredentialsException exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @PostMapping
    ResponseEntity<UserResponse> registerCustomer(@Valid @RequestBody Credentials request) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(userService.registerCustomer(request.username(), request.password())));
        } catch (UserService.DuplicateUsernameException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    public record Credentials(@NotBlank String username, @NotBlank String password) { }
    public record UserResponse(String username, Set<String> roles) {
        static UserResponse from(UserAccount user) {
            return new UserResponse(user.getUsername(), user.getRoles().stream().map(Enum::name).collect(java.util.stream.Collectors.toSet()));
        }
    }
}
