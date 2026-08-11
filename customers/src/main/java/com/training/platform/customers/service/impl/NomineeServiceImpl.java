package com.training.platform.customers.service.impl;

import com.training.platform.customers.dto.AddressRequest;
import com.training.platform.customers.dto.AddressResponse;
import com.training.platform.customers.dto.NomineeRequestDto;
import com.training.platform.customers.dto.NomineeResponseDto;
import com.training.platform.customers.entity.AddressEntity;
import com.training.platform.customers.entity.CustomerEntity;
import com.training.platform.customers.entity.NomineeEntity;
import com.training.platform.customers.exception.BadRequestException;
import com.training.platform.customers.exception.DuplicateResourceException;
import com.training.platform.customers.exception.ResourceNotFoundException;
import com.training.platform.customers.mapper.AddressMapper;
import com.training.platform.customers.repository.CustomerRepository;
import com.training.platform.customers.repository.NomineeRepository;
import com.training.platform.customers.security.CurrentUser;
import com.training.platform.customers.service.NomineeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NomineeServiceImpl implements NomineeService {

    private final NomineeRepository nomineeRepository;
    private final CustomerRepository customerRepository;
    private final AddressMapper addressMapper;

    @Override
    public NomineeResponseDto createNominee(Long customerId, NomineeRequestDto requestDto) {
        CustomerEntity customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with id " + customerId));

        if (requestDto.getNomineeName() == null || requestDto.getNomineeName().trim().isEmpty()) {
            throw new BadRequestException("Nominee name is required");
        }

        if (requestDto.getRelationType() == null || requestDto.getRelationType().trim().isEmpty()) {
            throw new BadRequestException("Relation type is required");
        }

        String status = requestDto.getStatus() == null || requestDto.getStatus().trim().isEmpty()
                ? "Active"
                : requestDto.getStatus();

        boolean duplicate = nomineeRepository
                .existsByCustomerCustomerIdAndNomineeNameIgnoreCaseAndRelationTypeIgnoreCaseAndStatusIgnoreCase(
                        customerId,
                        requestDto.getNomineeName(),
                        requestDto.getRelationType(),
                        "Active"
                );

        if (duplicate) {
            throw new DuplicateResourceException(
                    "Active nominee already exists for this customer with same name and relation type"
            );
        }

        NomineeEntity entity = new NomineeEntity();
        entity.setCustomer(customer);
        entity.setNomineeName(requestDto.getNomineeName());
        entity.setRelationship(requestDto.getRelationship());
        entity.setRelationType(requestDto.getRelationType());
        entity.setDob(requestDto.getDob());
        entity.setPhone(requestDto.getPhone());
        entity.setAddress(createAddress(requestDto.getAddress(), customer));
        entity.setSharePercentage(requestDto.getSharePercentage());
        entity.setStatus(status);
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setUpdatedBy(CurrentUser.id());
        entity.setStartDate(requestDto.getStartDate() != null ? requestDto.getStartDate() : LocalDate.now());
        entity.setEndDate(requestDto.getEndDate());

        return toResponse(nomineeRepository.save(entity));
    }

    @Override
    public List<NomineeResponseDto> getAllNomineesByCustomerId(Long customerId) {
        if (!customerRepository.existsById(customerId)) {
            throw new ResourceNotFoundException("Customer not found with id " + customerId);
        }

        return nomineeRepository.findByCustomerCustomerId(customerId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public NomineeResponseDto getNomineeById(Long customerId, Long nomineeId) {
        NomineeEntity nominee = nomineeRepository.findByNomineeIdAndCustomerCustomerId(nomineeId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Nominee not found with id " + nomineeId + " for customer " + customerId));

        return toResponse(nominee);
    }

    @Override
    public NomineeResponseDto updateNominee(Long customerId, Long nomineeId, NomineeRequestDto requestDto) {
        NomineeEntity nominee = nomineeRepository.findByNomineeIdAndCustomerCustomerId(nomineeId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Nominee not found with id " + nomineeId + " for customer " + customerId));

        if (requestDto.getNomineeName() == null || requestDto.getNomineeName().trim().isEmpty()) {
            throw new BadRequestException("Nominee name is required");
        }

        if (requestDto.getRelationType() == null || requestDto.getRelationType().trim().isEmpty()) {
            throw new BadRequestException("Relation type is required");
        }

        nominee.setNomineeName(requestDto.getNomineeName());
        nominee.setRelationship(requestDto.getRelationship());
        nominee.setRelationType(requestDto.getRelationType());
        nominee.setDob(requestDto.getDob());
        nominee.setPhone(requestDto.getPhone());
        updateAddress(nominee, requestDto.getAddress());
        nominee.setSharePercentage(requestDto.getSharePercentage());
        nominee.setStatus(requestDto.getStatus() == null ? nominee.getStatus() : requestDto.getStatus());
        nominee.setUpdatedBy(CurrentUser.id());
        nominee.setUpdatedAt(LocalDateTime.now());
        nominee.setStartDate(requestDto.getStartDate() != null ? requestDto.getStartDate() : nominee.getStartDate());
        nominee.setEndDate(requestDto.getEndDate());

        return toResponse(nomineeRepository.save(nominee));
    }

    @Override
    public NomineeResponseDto closeNominee(Long customerId, Long nomineeId) {
        NomineeEntity nominee = nomineeRepository.findByNomineeIdAndCustomerCustomerId(nomineeId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Nominee not found with id " + nomineeId + " for customer " + customerId));

        nominee.setStatus("Closed");
        nominee.setEndDate(LocalDate.now());
        nominee.setUpdatedAt(LocalDateTime.now());

        return toResponse(nomineeRepository.save(nominee));
    }

    private NomineeResponseDto toResponse(NomineeEntity entity) {
        NomineeResponseDto dto = new NomineeResponseDto();
        dto.setNomineeId(entity.getNomineeId());

        if (entity.getCustomer() != null) {
            dto.setCustomerId(entity.getCustomer().getCustomerId());
        }

        dto.setNomineeName(entity.getNomineeName());
        dto.setRelationship(entity.getRelationship());
        dto.setRelationType(entity.getRelationType());
        dto.setDob(entity.getDob());
        dto.setPhone(entity.getPhone());
        dto.setAddress(addressMapper.toResponse(entity.getAddress()));
        dto.setSharePercentage(entity.getSharePercentage());
        dto.setStatus(entity.getStatus());
        dto.setUpdatedAt(entity.getUpdatedAt());
        dto.setUpdatedBy(entity.getUpdatedBy());
        dto.setStartDate(entity.getStartDate());
        dto.setEndDate(entity.getEndDate());

        return dto;
    }

    private AddressEntity createAddress(AddressRequest requestDto, CustomerEntity customer) {
        if (requestDto == null) {
            return null;
        }

        validateAddress(requestDto);
        AddressEntity address = addressMapper.toEntity(requestDto);
        address.setCustomer(customer);
        return address;
    }

    private void updateAddress(NomineeEntity nominee, AddressRequest requestDto) {
        if (requestDto == null) {
            return;
        }

        validateAddress(requestDto);
        if (nominee.getAddress() == null) {
            nominee.setAddress(createAddress(requestDto, nominee.getCustomer()));
            return;
        }

        addressMapper.updateEntity(nominee.getAddress(), requestDto);
    }

    private void validateAddress(AddressRequest requestDto) {
        if (requestDto.addressType() == null) {
            throw new BadRequestException("Address type is required");
        }
        if (requestDto.line1() == null || requestDto.line1().isBlank()) {
            throw new BadRequestException("Address line1 is required");
        }
        if (requestDto.city() == null || requestDto.city().isBlank()) {
            throw new BadRequestException("Address city is required");
        }
        if (requestDto.state() == null || requestDto.state().isBlank()) {
            throw new BadRequestException("Address state is required");
        }
        if (requestDto.country() == null || requestDto.country().isBlank()) {
            throw new BadRequestException("Address country is required");
        }
        if (requestDto.pincode() == null || requestDto.pincode().isBlank()) {
            throw new BadRequestException("Address pincode is required");
        }
    }
}
