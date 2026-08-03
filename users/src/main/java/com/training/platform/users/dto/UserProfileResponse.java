package com.training.platform.users.dto;

import java.time.LocalDate;

public record UserProfileResponse(
        Long id,
        String firstName,
        String middleName,
        String lastName,
        String phoneNumber,
        LocalDate dateOfBirth,
        String addressLine1,
        String addressLine2,
        String city,
        String state,
        String postalCode,
        String countryCode
) {
}
