  package com.training.platform.customers.dto;

import com.training.platform.customers.constants.KycStatusType;
import com.training.platform.customers.constants.RiskLevelType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

    @Getter
    @Setter
    public class KycRequest{

        @NotNull(message = "KYC status is required")
        private KycStatusType kycStatus;

        @PastOrPresent(message = "KYC date cannot be in the future")
        private LocalDate kycDate;

        @Size(max = 100, message = "Verified by must not exceed 100 characters")
        private String verifiedBy;

        private RiskLevelType riskLevel;

        @Min(value = 0, message = "Risk score must be between 0 and 100")
        @Max(value = 100, message = "Risk score must be between 0 and 100")
        private Integer riskScore;

        private LocalDate expiryDate;

        @Size(max = 500, message = "Remarks must not exceed 500 characters")
        private String remarks;

        @Size(max = 100, message = "Updated by must not exceed 100 characters")
        private String updatedBy;

}
