package com.training.platform.users.service.impl;

import com.training.platform.auditclient.AuditClient;
import com.training.platform.users.dto.CreateUserRequest;
import com.training.platform.users.dto.UpdateUserRequest;
import com.training.platform.users.dto.UserProfileRequest;
import com.training.platform.users.dto.UserProfileResponse;
import com.training.platform.users.dto.UserResponse;
import com.training.platform.users.exception.UserConflictException;
import com.training.platform.users.exception.CurrentPasswordMismatchException;
import com.training.platform.users.exception.UserNotFoundException;
import com.training.platform.users.model.User;
import com.training.platform.users.model.UserProfile;
import com.training.platform.users.model.UserStatus;
import com.training.platform.users.repository.UserRepository;
import com.training.platform.users.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditClient auditClient;

    @Override
    @Transactional
    public UserResponse create(CreateUserRequest request) {
        String username = normalizeIdentity(request.username());
        String email = normalizeIdentity(request.email());
        ensureUniqueForCreate(username, email);

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(normalizeRole(request.role()));
        // Credentials supplied at creation are temporary for every platform user.
        user.setPasswordChangeRequired(true);
        user.setStatus(UserStatus.PENDING_VERIFICATION);
        user.attachProfile(toProfile(request.profile()));

        User savedUser = userRepository.save(user);
        log.info("User created id={}", savedUser.getId());
        auditUserChange(savedUser, "USER_CREATED", "User account created", Map.of(), userValues(savedUser));
        return toResponse(savedUser);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse get(Long id) {
        return toResponse(findUser(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserResponse> getAll(String query, Pageable pageable) {
        String normalizedQuery = clean(query);
        Page<User> users = normalizedQuery == null
                ? userRepository.findAll(pageable)
                : userRepository.search(normalizedQuery, pageable);
        return users.map(this::toResponse);
    }

    @Override
    @Transactional
    public UserResponse update(Long id, UpdateUserRequest request) {
        User user = findUser(id);
        Map<String, Object> previousValues = userValues(user);
        String username = normalizeIdentity(request.username());
        String email = normalizeIdentity(request.email());
        ensureUniqueForUpdate(id, username, email);

        user.setUsername(username);
        user.setEmail(email);
        user.setRole(normalizeRole(request.role()));
        applyProfile(user, request.profile());
        log.info("User updated id={}", id);
        auditUserChange(user, "USER_UPDATED", "User fields changed", previousValues, userValues(user));
        return toResponse(user);
    }

    @Override
    @Transactional
    public void updatePassword(Long id, String rawPassword) {
        User user = findUser(id);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setPasswordChangeRequired(true);
        log.info("Password updated id={}", id);
        Map<String, Object> passwordDetails = new LinkedHashMap<>();
        passwordDetails.put("targetUserId", id);
        passwordDetails.put("changedFields", "password");
        passwordDetails.put("oldValuesJson", "{\"password\":\"[REDACTED]\"}");
        passwordDetails.put("newValuesJson", "{\"password\":\"[REDACTED]\"}");
        auditClient.success("users", "PASSWORD_CHANGED", "User password changed", passwordDetails);
    }

    @Override
    @Transactional
    public void changeOwnPassword(Long id, String currentPassword, String newPassword) {
        User user = findUser(id);
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new CurrentPasswordMismatchException();
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordChangeRequired(false);
        log.info("User changed own password id={}", id);
        Map<String, Object> passwordDetails = new LinkedHashMap<>();
        passwordDetails.put("targetUserId", id);
        passwordDetails.put("changedFields", "password");
        passwordDetails.put("oldValuesJson", "{\"password\":\"[REDACTED]\"}");
        passwordDetails.put("newValuesJson", "{\"password\":\"[REDACTED]\"}");
        auditClient.success("users", "OWN_PASSWORD_CHANGED", "User changed own password", passwordDetails);
    }

    @Override
    @Transactional
    public UserResponse updateStatus(Long id, UserStatus status) {
        User user = findUser(id);
        if (!"EMPLOYEE".equalsIgnoreCase(user.getRole())) {
            throw new UserConflictException("Only employee account statuses can be changed from the admin control.");
        }
        String previousStatus = user.getStatus().name();
        user.setStatus(status);
        log.info("User status updated id={} status={}", id, status);
        auditStatusChange(user, "USER_STATUS_CHANGED", "User status changed", previousStatus, status.name());
        return toResponse(user);
    }

    @Override
    @Transactional
    public void deactivate(Long id) {
        User user = findUser(id);
        String previousStatus = user.getStatus().name();
        user.setStatus(UserStatus.DEACTIVATED);
        log.info("User deactivated id={}", id);
        auditStatusChange(user, "USER_DEACTIVATED", "User account deactivated",
                previousStatus, UserStatus.DEACTIVATED.name());
    }

    private User findUser(Long id) {
        return userRepository.findWithProfileById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
    }

    private void ensureUniqueForCreate(String username, String email) {
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new UserConflictException("Username is already in use");
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new UserConflictException("Email is already in use");
        }
    }

    private void ensureUniqueForUpdate(Long id, String username, String email) {
        if (userRepository.existsByUsernameIgnoreCaseAndIdNot(username, id)) {
            throw new UserConflictException("Username is already in use");
        }
        if (userRepository.existsByEmailIgnoreCaseAndIdNot(email, id)) {
            throw new UserConflictException("Email is already in use");
        }
    }

    private void applyProfile(User user, UserProfileRequest request) {
        UserProfile profile = user.getProfile();
        if (profile == null) {
            profile = new UserProfile();
            user.attachProfile(profile);
        }
        copyProfileFields(profile, request);
    }

    private UserProfile toProfile(UserProfileRequest request) {
        UserProfile profile = new UserProfile();
        copyProfileFields(profile, request);
        return profile;
    }

    private void copyProfileFields(UserProfile profile, UserProfileRequest request) {
        profile.setFirstName(request.firstName().trim());
        profile.setMiddleName(clean(request.middleName()));
        profile.setLastName(request.lastName().trim());
        profile.setPhoneNumber(clean(request.phoneNumber()));
        profile.setDateOfBirth(request.dateOfBirth());
        profile.setAddressLine1(clean(request.addressLine1()));
        profile.setAddressLine2(clean(request.addressLine2()));
        profile.setCity(clean(request.city()));
        profile.setState(clean(request.state()));
        profile.setPostalCode(clean(request.postalCode()));
        profile.setCountryCode(request.countryCode() == null
                ? null
                : request.countryCode().trim().toUpperCase(Locale.ROOT));
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole(),
                user.getStatus(),
                toProfileResponse(user.getProfile()),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                user.getVersion()
        );
    }

    private UserProfileResponse toProfileResponse(UserProfile profile) {
        if (profile == null) {
            return null;
        }
        return new UserProfileResponse(
                profile.getId(),
                profile.getFirstName(),
                profile.getMiddleName(),
                profile.getLastName(),
                profile.getPhoneNumber(),
                profile.getDateOfBirth(),
                profile.getAddressLine1(),
                profile.getAddressLine2(),
                profile.getCity(),
                profile.getState(),
                profile.getPostalCode(),
                profile.getCountryCode()
        );
    }

    private String normalizeIdentity(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeRole(String role) {
        return role.trim().toUpperCase(Locale.ROOT);
    }

    private String clean(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private Map<String, Object> userValues(User user) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("username", user.getUsername());
        values.put("email", user.getEmail());
        values.put("role", user.getRole());
        values.put("status", user.getStatus() == null ? null : user.getStatus().name());
        UserProfile profile = user.getProfile();
        if (profile != null) {
            values.put("firstName", profile.getFirstName());
            values.put("middleName", profile.getMiddleName());
            values.put("lastName", profile.getLastName());
            values.put("phoneNumber", profile.getPhoneNumber());
            values.put("dateOfBirth", profile.getDateOfBirth());
            values.put("addressLine1", profile.getAddressLine1());
            values.put("addressLine2", profile.getAddressLine2());
            values.put("city", profile.getCity());
            values.put("state", profile.getState());
            values.put("postalCode", profile.getPostalCode());
            values.put("countryCode", profile.getCountryCode());
        }
        return values;
    }

    private void auditUserChange(User user, String action, String description,
                                 Map<String, ?> previousValues, Map<String, ?> newValues) {
        Map<String, Object> changes = auditClient.changes(previousValues, newValues);
        if (changes != null && changes.isEmpty()) return;
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("targetUserId", user.getId());
        details.put("newStatus", user.getStatus() == null ? null : user.getStatus().name());
        details.put("newRole", user.getRole());
        if (changes != null) {
            details.putAll(changes);
            description = description + ": " + changes.get("changedFields");
        }
        auditClient.success("users", action, description, details);
    }

    private void auditStatusChange(User user, String action, String description,
                                   String previousStatus, String newStatus) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("targetUserId", user.getId());
        details.put("previousStatus", previousStatus);
        details.put("newStatus", newStatus);
        Map<String, Object> changes = auditClient.changes(
                Map.of("status", previousStatus), Map.of("status", newStatus));
        if (changes != null) details.putAll(changes);
        auditClient.success("users", action, description, details);
    }
}
