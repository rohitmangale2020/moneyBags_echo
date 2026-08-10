package com.training.platform.customers.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
public class NomineeResponseDto {

    private Long nomineeId;
    private Long customerId;
    private String nomineeName;
    private String relationship;
    private String relationType;
    private LocalDate dob;
    private String phone;
    private String address;
    private Double sharePercentage;
    private String status;
    private LocalDateTime updatedAt;
    private String updatedBy;
    private LocalDate startDate;
    private LocalDate endDate;
}