package com.training.platform.customers.dto;

import com.training.platform.customers.constants.AddressType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AddressRequest(
        @NotNull(message = "Address type is required") AddressType addressType,
        @NotBlank(message = "Address line 1 is required")
        @Size(max = 250, message = "Address line 1 must not exceed 250 characters") String line1,
        @Size(max = 250, message = "Address line 2 must not exceed 250 characters") String line2,
        @NotBlank(message = "City is required")
        @Size(max = 100, message = "City must not exceed 100 characters") String city,
        @NotBlank(message = "State is required")
        @Size(max = 100, message = "State must not exceed 100 characters") String state,
        @NotBlank(message = "Country is required")
        @Size(max = 100, message = "Country must not exceed 100 characters") String country,
        @NotBlank(message = "Pincode is required")
        @Pattern(regexp = "^[1-9][0-9]{5}$", message = "Pincode must be a valid 6-digit Indian pincode") String pincode
) {
}
