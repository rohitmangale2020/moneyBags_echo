package com.training.platform.customers.entity;

import com.training.platform.customers.constants.KycStatusType;
import com.training.platform.customers.constants.RiskLevelType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name ="customerkyc")
public class KycEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name ="kyc_id")
    private Long kycId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false, unique = true)
    private CustomerEntity customer;

    @Column(name = "kyc_status", nullable = false, length = 20)
    private KycStatusType kycStatus; // Pending, Verified, Not Started, rejected

    @Column(name = "kyc_date")
    private LocalDate kycDate;

    @Column(name = "verified_by", length = 100)
    private String verifiedBy;

    @Column(name = "verified_on")
    private LocalDateTime verifiedOn;

    @Column(name = "risk_level", length = 50)
    private RiskLevelType riskLevel; // LOW, MEDIUM, HIGH

    @Column(name = "risk_score")
    private Integer riskScore;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "remarks", length = 500)
    private String remarks;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @Column(name = "updated_on")
    private LocalDateTime updatedOn;

}
