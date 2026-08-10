package com.training.platform.customers.dto;

import com.training.platform.customers.constants.KycStatusType;
import com.training.platform.customers.constants.RiskLevelType;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
public class KycResponse {

    private Long kycId;
    private Long customerId;
    private KycStatusType kycStatus;
    private LocalDate kycDate;
    private String verifiedBy;
    private LocalDateTime verifiedOn;
    private RiskLevelType riskLevel;
    private Integer riskScore;
    private LocalDate expiryDate;
    private String remarks;
    private String updatedBy;
    private LocalDateTime updatedOn;
}