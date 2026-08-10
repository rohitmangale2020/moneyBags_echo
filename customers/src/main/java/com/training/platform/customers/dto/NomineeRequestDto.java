package com.training.platform.customers.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class NomineeRequestDto {

    @NotBlank(message = "Nominee name is required")
    private String nomineeName;

    private String relationship;

    @NotBlank(message = "Relation type is required")
    private String relationType;

    private LocalDate dob;

    private String phone;

    private String address;

    private Double sharePercentage;

    private String status;

    private String updatedBy;

    private LocalDate startDate;

    private LocalDate endDate;
}