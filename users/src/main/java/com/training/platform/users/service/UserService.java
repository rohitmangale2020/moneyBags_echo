package com.training.platform.users.service;

import com.training.platform.users.dto.CreateUserRequest;
import com.training.platform.users.dto.UpdateUserRequest;
import com.training.platform.users.dto.UserResponse;
import com.training.platform.users.model.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserService {

    UserResponse create(CreateUserRequest request);

    UserResponse get(Long id);

    Page<UserResponse> getAll(String query, Pageable pageable);

    UserResponse update(Long id, UpdateUserRequest request);

    void updatePassword(Long id, String rawPassword);

    void changeOwnPassword(Long id, String currentPassword, String newPassword);

    UserResponse updateStatus(Long id, UserStatus status);

    void deactivate(Long id);
}
