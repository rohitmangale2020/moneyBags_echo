package com.training.platform.customers.dto;

import com.training.platform.customers.constants.Gender;

import java.time.LocalDate;

public record CustomerRequest(
        String firstName,
        String lastName,
        LocalDate dob,
        Gender gender,
        String phone,
        String email,
        String occupation
) {
}