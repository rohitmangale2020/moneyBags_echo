  package com.training.platform.customers.dto;

import com.training.platform.customers.constants.KycStatusType;
import com.training.platform.customers.constants.RiskLevelType;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

    @Getter
    @Setter
    public class KycRequest{

        @NotNull(message = "KYC status is required")
        private KycStatusType kycStatus;

        private LocalDate kycDate;

        private String verifiedBy;

        private RiskLevelType riskLevel;

        private Integer riskScore;

        private LocalDate expiryDate;

        private String remarks;

        private String updatedBy;

}
