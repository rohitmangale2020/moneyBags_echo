package com.training.platform.customers.dto;

import com.training.platform.customers.constants.CustomerStatus;
import com.training.platform.customers.constants.Gender;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record CustomerResponse(
        Long customerId,
        String cifNo,
        String firstName,
        String lastName,
        LocalDate dob,
        Gender gender,
        String phone,
        String email,
        String occupation,
        CustomerStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
