package com.training.platform.users.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UserProfileRequest(
        @NotBlank @Size(max = 80) String firstName,
        @Size(max = 80) String middleName,
        @NotBlank @Size(max = 80) String lastName,
        @Pattern(regexp = "^\\+?[1-9]\\d{7,14}$", message = "must be a valid international phone number")
        String phoneNumber,
        @Past LocalDate dateOfBirth,
        @Size(max = 150) String addressLine1,
        @Size(max = 150) String addressLine2,
        @Size(max = 80) String city,
        @Size(max = 80) String state,
        @Size(max = 20) String postalCode,
        @Pattern(regexp = "^[A-Za-z]{2}$", message = "must be a two-letter ISO country code")
        String countryCode
) {
}
