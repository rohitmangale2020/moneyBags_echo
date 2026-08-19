package com.training.platform.users.service.impl;

import com.training.platform.auditclient.AuditClient;
import com.training.platform.users.dto.CreateUserRequest;
import com.training.platform.users.dto.UpdateUserRequest;
import com.training.platform.users.dto.UserProfileRequest;
import com.training.platform.users.dto.UserResponse;
import com.training.platform.users.exception.UserConflictException;
import com.training.platform.users.exception.CurrentPasswordMismatchException;
import com.training.platform.users.exception.UserNotFoundException;
import com.training.platform.users.model.User;
import com.training.platform.users.model.UserProfile;
import com.training.platform.users.model.UserStatus;
import com.training.platform.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuditClient auditClient;

    @InjectMocks
    private UserServiceImpl userService;

    @Captor
    private ArgumentCaptor<User> userCaptor;

    private CreateUserRequest createRequest;

    @BeforeEach
    void setUp() {
        createRequest = new CreateUserRequest(
                "  Priyansh  ", "  PRIYANSH@example.com ", "password123", " customer ", profileRequest());
    }

    @Test
    void create_validRequest_returnsNormalizedUser() {
        User savedUser = user(1L, "priyansh", "priyansh@example.com");
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        UserResponse response = userService.create(createRequest);

        verify(userRepository).save(userCaptor.capture());
        User capturedUser = userCaptor.getValue();
        assertEquals("priyansh", capturedUser.getUsername());
        assertEquals("priyansh@example.com", capturedUser.getEmail());
        assertEquals("encoded-password", capturedUser.getPasswordHash());
        assertEquals("CUSTOMER", capturedUser.getRole());
        assertEquals(UserStatus.ACTIVE, capturedUser.getStatus());
        assertEquals(true, capturedUser.isPasswordChangeRequired());
        assertEquals("priyansh", response.username());
    }

    @Test
    void create_duplicateUsername_throwsUserConflictException() {
        when(userRepository.existsByUsernameIgnoreCase("priyansh")).thenReturn(true);

        assertThrows(UserConflictException.class, () -> userService.create(createRequest));

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void get_missingId_throwsUserNotFoundException() {
        when(userRepository.findWithProfileById(99L)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> userService.get(99L));
    }

    @Test
    void getAll_pageableInput_returnsUserPage() {
        User savedUser = user(1L, "priyansh", "priyansh@example.com");
        when(userRepository.findAll(any(PageRequest.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of(savedUser)));

        var response = userService.getAll(null, PageRequest.of(0, 20));

        assertEquals(1, response.getTotalElements());
        assertEquals("priyansh", response.getContent().get(0).username());
    }

    @Test
    void getAll_query_usesDatabaseSearch() {
        User savedUser = user(1L, "priyansh", "priyansh@example.com");
        PageRequest pageable = PageRequest.of(0, 20);
        when(userRepository.search("priy", pageable))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of(savedUser)));

        var response = userService.getAll("  priy  ", pageable);

        assertEquals(1, response.getTotalElements());
        verify(userRepository).search("priy", pageable);
    }

    @Test
    void update_validRequest_returnsUpdatedUser() {
        User existingUser = user(1L, "old-user", "old@example.com");
        UpdateUserRequest request = new UpdateUserRequest("new-user", "NEW@example.com", "admin", profileRequest());
        when(userRepository.findWithProfileById(1L)).thenReturn(Optional.of(existingUser));

        UserResponse response = userService.update(1L, request);

        assertEquals("new-user", existingUser.getUsername());
        assertEquals("new@example.com", existingUser.getEmail());
        assertEquals("ADMIN", existingUser.getRole());
        assertEquals("new-user", response.username());
    }

    @Test
    void update_duplicateEmail_throwsUserConflictException() {
        when(userRepository.findWithProfileById(1L)).thenReturn(Optional.of(user(1L, "user", "user@example.com")));
        when(userRepository.existsByEmailIgnoreCaseAndIdNot("new@example.com", 1L)).thenReturn(true);
        UpdateUserRequest request = new UpdateUserRequest("new-user", "new@example.com", "CUSTOMER", profileRequest());

        assertThrows(UserConflictException.class, () -> userService.update(1L, request));
    }

    @Test
    void updatePassword_validPassword_encodesPassword() {
        User existingUser = user(1L, "priyansh", "priyansh@example.com");
        when(userRepository.findWithProfileById(1L)).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.encode("new-password")).thenReturn("new-encoded-password");

        userService.updatePassword(1L, "new-password");

        assertEquals("new-encoded-password", existingUser.getPasswordHash());
        assertEquals(true, existingUser.isPasswordChangeRequired());
    }

    @Test
    void create_employee_requiresPasswordChange() {
        CreateUserRequest employee = new CreateUserRequest(
                "employee", "employee@example.com", "password123", "EMPLOYEE", profileRequest());
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenReturn(user(1L, "employee", "employee@example.com"));

        userService.create(employee);

        verify(userRepository).save(userCaptor.capture());
        assertEquals(true, userCaptor.getValue().isPasswordChangeRequired());
    }

    @Test
    void changeOwnPassword_replacesPasswordAndClearsRequirement() {
        User existingUser = user(1L, "priyansh", "priyansh@example.com");
        existingUser.setPasswordChangeRequired(true);
        when(userRepository.findWithProfileById(1L)).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches("temporary-password", "encoded-password")).thenReturn(true);
        when(passwordEncoder.encode("new-password")).thenReturn("new-encoded-password");

        userService.changeOwnPassword(1L, "temporary-password", "new-password");

        assertEquals("new-encoded-password", existingUser.getPasswordHash());
        assertEquals(false, existingUser.isPasswordChangeRequired());
    }

    @Test
    void changeOwnPassword_withIncorrectCurrentPassword_isRejected() {
        User existingUser = user(1L, "priyansh", "priyansh@example.com");
        when(userRepository.findWithProfileById(1L)).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches("wrong-password", "encoded-password")).thenReturn(false);

        assertThrows(CurrentPasswordMismatchException.class,
                () -> userService.changeOwnPassword(1L, "wrong-password", "new-password"));
    }

    @Test
    void updateStatus_activeStatus_returnsActiveUser() {
        User existingUser = user(1L, "priyansh", "priyansh@example.com");
        existingUser.setRole("EMPLOYEE");
        when(userRepository.findWithProfileById(1L)).thenReturn(Optional.of(existingUser));

        UserResponse response = userService.updateStatus(1L, UserStatus.ACTIVE);

        assertEquals(UserStatus.ACTIVE, existingUser.getStatus());
        assertEquals(UserStatus.ACTIVE, response.status());
    }

    @Test
    void deactivate_existingId_setsDeactivatedStatus() {
        User existingUser = user(1L, "priyansh", "priyansh@example.com");
        when(userRepository.findWithProfileById(1L)).thenReturn(Optional.of(existingUser));

        userService.deactivate(1L);

        assertEquals(UserStatus.DEACTIVATED, existingUser.getStatus());
    }

    private UserProfileRequest profileRequest() {
        return new UserProfileRequest(
                " Priyansh ", null, " Pachauri ", "+919876543210", LocalDate.of(2000, 1, 1),
                null, null, "Bengaluru", "Karnataka", "560001", "in"
        );
    }

    private User user(Long id, String username, String email) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash("encoded-password");
        user.setRole("CUSTOMER");
        user.setStatus(UserStatus.ACTIVE);
        UserProfile profile = new UserProfile();
        profile.setFirstName("Priyansh");
        profile.setLastName("Pachauri");
        user.attachProfile(profile);
        return user;
    }
}
