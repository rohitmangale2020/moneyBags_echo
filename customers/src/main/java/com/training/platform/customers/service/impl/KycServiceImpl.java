package com.training.platform.customers.service.impl;

import com.training.platform.auditclient.AuditClient;
import com.training.platform.customers.dto.KycRequest;
import com.training.platform.customers.dto.KycResponse;
import com.training.platform.customers.entity.CustomerEntity;
import com.training.platform.customers.entity.KycEntity;
import com.training.platform.customers.exception.BadRequestException;
import com.training.platform.customers.exception.DuplicateResourceException;
import com.training.platform.customers.exception.ResourceNotFoundException;
import com.training.platform.customers.repository.CustomerRepository;
import com.training.platform.customers.repository.KycRepository;
import com.training.platform.customers.repository.AddressRepository;
import com.training.platform.customers.repository.DocumentRepository;
import com.training.platform.customers.security.CurrentUser;
import com.training.platform.customers.service.KycService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class KycServiceImpl implements KycService {

    private final KycRepository kycRepository;
    private final CustomerRepository customerRepository;
    private final AuditClient auditClient;
    private final AddressRepository addressRepository;
    private final DocumentRepository documentRepository;

    @Override
    public KycResponse createKyc(Long customerId, KycRequest requestDto) {
        CustomerEntity customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with id " + customerId));

        if (kycRepository.existsByCustomerCustomerId(customerId)) {
            throw new DuplicateResourceException("KYC already exists for customer id " + customerId);
        }
        validateVerificationEvidence(customerId, requestDto);

        KycEntity kyc = new KycEntity();
        kyc.setCustomer(customer);
        kyc.setKycStatus(requestDto.getKycStatus());
        kyc.setKycDate(requestDto.getKycDate());
        kyc.setVerifiedBy(requestDto.getKycStatus().name().equalsIgnoreCase("VERIFIED") ? CurrentUser.id() : null);
        kyc.setRiskLevel(requestDto.getRiskLevel());
        kyc.setRiskScore(requestDto.getRiskScore());
        kyc.setExpiryDate(requestDto.getExpiryDate());
        kyc.setRemarks(requestDto.getRemarks());
        kyc.setUpdatedBy(CurrentUser.id());
        kyc.setUpdatedOn(LocalDateTime.now());

        if (requestDto.getKycStatus() != null
                && requestDto.getKycStatus().name().equalsIgnoreCase("VERIFIED")
                && requestDto.getVerifiedBy() != null) {
            kyc.setVerifiedOn(LocalDateTime.now());
        }

        KycEntity saved = kycRepository.save(kyc);
        auditKycChange(customerId, saved, "KYC_CREATED", "Customer KYC record created",
                Map.of(), kycValues(saved));
        return toResponse(saved);
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
        Map<String, Object> previousValues = kycValues(kyc);

        if (requestDto.getKycStatus() == null) {
            throw new BadRequestException("KYC status is required");
        }
        validateVerificationEvidence(customerId, requestDto);

        kyc.setKycStatus(requestDto.getKycStatus());
        kyc.setKycDate(requestDto.getKycDate());
        kyc.setVerifiedBy(requestDto.getKycStatus().name().equalsIgnoreCase("VERIFIED") ? CurrentUser.id() : null);
        kyc.setRiskLevel(requestDto.getRiskLevel());
        kyc.setRiskScore(requestDto.getRiskScore());
        kyc.setExpiryDate(requestDto.getExpiryDate());
        kyc.setRemarks(requestDto.getRemarks());
        kyc.setUpdatedBy(CurrentUser.id());
        kyc.setUpdatedOn(LocalDateTime.now());

        if (requestDto.getKycStatus().name().equalsIgnoreCase("VERIFIED")) {
            if (kyc.getVerifiedOn() == null) {
                kyc.setVerifiedOn(LocalDateTime.now());
            }
        } else {
            kyc.setVerifiedOn(null);
        }

        KycEntity saved = kycRepository.save(kyc);
        auditKycChange(customerId, saved, "KYC_UPDATED", "KYC fields changed",
                previousValues, kycValues(saved));
        return toResponse(saved);
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

    private Map<String, Object> kycValues(KycEntity kyc) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("kycStatus", kyc.getKycStatus() == null ? null : kyc.getKycStatus().name());
        values.put("kycDate", kyc.getKycDate());
        values.put("verifiedBy", kyc.getVerifiedBy());
        values.put("verifiedOn", kyc.getVerifiedOn());
        values.put("riskLevel", kyc.getRiskLevel() == null ? null : kyc.getRiskLevel().name());
        values.put("riskScore", kyc.getRiskScore());
        values.put("expiryDate", kyc.getExpiryDate());
        values.put("remarks", kyc.getRemarks());
        return values;
    }

    private void auditKycChange(Long customerId, KycEntity kyc, String action, String description,
                                Map<String, ?> previousValues, Map<String, ?> newValues) {
        Map<String, Object> changes = auditClient.changes(previousValues, newValues);
        if (changes != null && changes.isEmpty()) return;
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("customerId", customerId);
        details.put("relatedEntityType", "KYC");
        details.put("relatedEntityId", kyc.getKycId().toString());
        details.put("previousStatus", previousValues.get("kycStatus"));
        details.put("newStatus", newValues.get("kycStatus"));
        if (changes != null) {
            details.putAll(changes);
            if (!previousValues.isEmpty()) description += ": " + changes.get("changedFields");
        }
        auditClient.success("customers", action, description, details);
      
    }
    private void validateVerificationEvidence(Long customerId, KycRequest requestDto) {
        if (requestDto == null || requestDto.getKycStatus() == null
                || !requestDto.getKycStatus().name().equalsIgnoreCase("VERIFIED")) {
            return;
        }
        if (!addressRepository.existsByCustomerCustomerId(customerId)) {
            throw new BadRequestException("At least one customer address is required before KYC can be verified.");
        }
        if (!documentRepository.existsByCustomerCustomerId(customerId)) {
            throw new BadRequestException("At least one customer document is required before KYC can be verified.");
        }
    }


