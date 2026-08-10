package com.training.platform.customers.service.impl;

import com.training.platform.customers.dto.KycRequest;
import com.training.platform.customers.dto.KycResponse;
import com.training.platform.customers.entity.CustomerEntity;
import com.training.platform.customers.entity.KycEntity;
import com.training.platform.customers.exception.BadRequestException;
import com.training.platform.customers.exception.DuplicateResourceException;
import com.training.platform.customers.exception.ResourceNotFoundException;
import com.training.platform.customers.repository.CustomerRepository;
import com.training.platform.customers.repository.KycRepository;
import com.training.platform.customers.service.KycService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class KycServiceImpl implements KycService {

    private final KycRepository kycRepository;
    private final CustomerRepository customerRepository;

    @Override
    public KycResponse createKyc(Long customerId, KycRequest requestDto) {
        CustomerEntity customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with id " + customerId));

        if (kycRepository.existsByCustomerCustomerId(customerId)) {
            throw new DuplicateResourceException("KYC already exists for customer id " + customerId);
        }

        KycEntity kyc = new KycEntity();
        kyc.setCustomer(customer);
        kyc.setKycStatus(requestDto.getKycStatus());
        kyc.setKycDate(requestDto.getKycDate());
        kyc.setVerifiedBy(requestDto.getVerifiedBy());
        kyc.setRiskLevel(requestDto.getRiskLevel());
        kyc.setRiskScore(requestDto.getRiskScore());
        kyc.setExpiryDate(requestDto.getExpiryDate());
        kyc.setRemarks(requestDto.getRemarks());
        kyc.setUpdatedBy(requestDto.getUpdatedBy());
        kyc.setUpdatedOn(LocalDateTime.now());

        if (requestDto.getKycStatus() != null
                && requestDto.getKycStatus().name().equalsIgnoreCase("VERIFIED")
                && requestDto.getVerifiedBy() != null) {
            kyc.setVerifiedOn(LocalDateTime.now());
        }

        return toResponse(kycRepository.save(kyc));
    }

    @Override
    public KycResponse getKycByCustomerId(Long customerId) {
        if (!customerRepository.existsById(customerId)) {
            throw new ResourceNotFoundException("Customer not found with id " + customerId);
        }

        KycEntity kyc = kycRepository.findByCustomerCustomerId(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("KYC not found for customer id " + customerId));

        return toResponse(kyc);
    }

    @Override
    public KycResponse updateKyc(Long customerId, KycRequest requestDto) {
        KycEntity kyc = kycRepository.findByCustomerCustomerId(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("KYC not found for customer id " + customerId));

        if (requestDto.getKycStatus() == null) {
            throw new BadRequestException("KYC status is required");
        }

        kyc.setKycStatus(requestDto.getKycStatus());
        kyc.setKycDate(requestDto.getKycDate());
        kyc.setVerifiedBy(requestDto.getVerifiedBy());
        kyc.setRiskLevel(requestDto.getRiskLevel());
        kyc.setRiskScore(requestDto.getRiskScore());
        kyc.setExpiryDate(requestDto.getExpiryDate());
        kyc.setRemarks(requestDto.getRemarks());
        kyc.setUpdatedBy(requestDto.getUpdatedBy());
        kyc.setUpdatedOn(LocalDateTime.now());

        if (requestDto.getKycStatus().name().equalsIgnoreCase("VERIFIED")) {
            if (kyc.getVerifiedOn() == null) {
                kyc.setVerifiedOn(LocalDateTime.now());
            }
        } else {
            kyc.setVerifiedOn(null);
        }

        return toResponse(kycRepository.save(kyc));
    }

    private KycResponse toResponse(KycEntity entity) {
        KycResponse dto = new KycResponse();
        dto.setKycId(entity.getKycId());

        if (entity.getCustomer() != null) {
            dto.setCustomerId(entity.getCustomer().getCustomerId());
        }

        dto.setKycStatus(entity.getKycStatus());
        dto.setKycDate(entity.getKycDate());
        dto.setVerifiedBy(entity.getVerifiedBy());
        dto.setVerifiedOn(entity.getVerifiedOn());
        dto.setRiskLevel(entity.getRiskLevel());
        dto.setRiskScore(entity.getRiskScore());
        dto.setExpiryDate(entity.getExpiryDate());
        dto.setRemarks(entity.getRemarks());
        dto.setUpdatedBy(entity.getUpdatedBy());
        dto.setUpdatedOn(entity.getUpdatedOn());

        return dto;
    }
}