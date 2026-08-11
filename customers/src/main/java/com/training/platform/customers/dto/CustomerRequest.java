package com.training.platform.customers.dto;

import com.training.platform.customers.constants.Gender;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CustomerRequest(
        @NotBlank(message = "First name is required")
        @Size(max = 100, message = "First name must not exceed 100 characters") String firstName,
        @Size(max = 100, message = "Last name must not exceed 100 characters") String lastName,
        @NotNull(message = "Date of birth is required")
        @Past(message = "Date of birth must be in the past") LocalDate dob,
        @NotNull(message = "Gender is required") Gender gender,
        @NotBlank(message = "Phone number is required")
        @Pattern(regexp = "^[6-9][0-9]{9}$", message = "Phone number must be a valid 10-digit Indian mobile number") String phone,
        @Email(message = "Email must be valid")
        @Size(max = 254, message = "Email must not exceed 254 characters") String email,
        @Size(max = 100, message = "Occupation must not exceed 100 characters") String occupation
) {
}
