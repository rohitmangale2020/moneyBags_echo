package com.training.platform.users.service.impl;

import com.training.platform.users.dto.CreateUserRequest;
import com.training.platform.users.dto.UpdateUserRequest;
import com.training.platform.users.dto.UserProfileRequest;
import com.training.platform.users.dto.UserProfileResponse;
import com.training.platform.users.dto.UserResponse;
import com.training.platform.users.exception.UserConflictException;
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

import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

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
        user.setStatus(UserStatus.PENDING_VERIFICATION);
        user.attachProfile(toProfile(request.profile()));

        User savedUser = userRepository.save(user);
        log.info("User created id={}", savedUser.getId());
        return toResponse(savedUser);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse get(Long id) {
        return toResponse(findUser(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserResponse> getAll(Pageable pageable) {
        return userRepository.findAll(pageable).map(this::toResponse);
    }

    @Override
    @Transactional
    public UserResponse update(Long id, UpdateUserRequest request) {
        User user = findUser(id);
        String username = normalizeIdentity(request.username());
        String email = normalizeIdentity(request.email());
        ensureUniqueForUpdate(id, username, email);

        user.setUsername(username);
        user.setEmail(email);
        user.setRole(normalizeRole(request.role()));
        applyProfile(user, request.profile());
        log.info("User updated id={}", id);
        return toResponse(user);
    }

    @Override
    @Transactional
    public void updatePassword(Long id, String rawPassword) {
        User user = findUser(id);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        log.info("Password updated id={}", id);
    }

    @Override
    @Transactional
    public UserResponse updateStatus(Long id, UserStatus status) {
        User user = findUser(id);
        user.setStatus(status);
        log.info("User status updated id={} status={}", id, status);
        return toResponse(user);
    }

    @Override
    @Transactional
    public void deactivate(Long id) {
        User user = findUser(id);
        user.setStatus(UserStatus.DEACTIVATED);
        log.info("User deactivated id={}", id);
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
}
