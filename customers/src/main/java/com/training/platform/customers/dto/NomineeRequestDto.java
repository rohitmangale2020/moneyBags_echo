package com.training.platform.customers.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class NomineeRequestDto {

    @NotBlank(message = "Nominee name is required")
    @Size(max = 150, message = "Nominee name must not exceed 150 characters")
    private String nomineeName;

    @Size(max = 100, message = "Relationship must not exceed 100 characters")
    private String relationship;

    @NotBlank(message = "Relation type is required")
    @Pattern(regexp = "(?i)NOMINEE|JOIN_HOLDER|GUARDIAN|AUTHORIZED_SIGNATORY", message = "Relation type must be NOMINEE, JOIN_HOLDER, GUARDIAN, or AUTHORIZED_SIGNATORY")
    private String relationType;

    @Past(message = "Nominee date of birth must be in the past")
    private LocalDate dob;

    @Pattern(regexp = "^$|^[6-9][0-9]{9}$", message = "Phone number must be a valid 10-digit Indian mobile number")
    private String phone;

    @Valid
    private AddressRequest address;

    @DecimalMin(value = "0.01", message = "Share percentage must be greater than 0")
    @DecimalMax(value = "100.00", message = "Share percentage must not exceed 100")
    private Double sharePercentage;

    @Pattern(regexp = "(?i)ACTIVE|INACTIVE|PENDING|CLOSED", message = "Status must be ACTIVE, INACTIVE, PENDING, or CLOSED")
    private String status;

    @Size(max = 100, message = "Updated by must not exceed 100 characters")
    private String updatedBy;

    private LocalDate startDate;

    private LocalDate endDate;
}
