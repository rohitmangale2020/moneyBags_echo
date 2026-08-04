package com.training.platform.users.service;

import com.training.platform.users.model.Role;
import com.training.platform.users.model.UserAccount;
import com.training.platform.users.repository.UserRepository;
import java.util.Set;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    public UserAccount authenticate(String username, String password) {
        UserAccount user = users.findByUsername(username).orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(password, user.getPasswordHash())) throw new InvalidCredentialsException();
        return user;
    }

    public UserAccount registerCustomer(String username, String password) { return createUser(username, password, Set.of(Role.CUSTOMER)); }

    public UserAccount createUser(String username, String password, Set<Role> roles) {
        if (users.findByUsername(username).isPresent()) throw new DuplicateUsernameException();
        return users.save(new UserAccount(username, passwordEncoder.encode(password), roles));
    }

    public static class InvalidCredentialsException extends RuntimeException { }
    public static class DuplicateUsernameException extends RuntimeException { }
}
