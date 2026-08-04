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

    Page<UserResponse> getAll(Pageable pageable);

    UserResponse update(Long id, UpdateUserRequest request);

    void updatePassword(Long id, String rawPassword);

    UserResponse updateStatus(Long id, UserStatus status);

    void deactivate(Long id);
}
