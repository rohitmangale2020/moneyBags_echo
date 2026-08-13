package com.training.platform.customers.service.impl;

import com.training.platform.customers.constants.CustomerStatus;
import com.training.platform.customers.constants.KycStatusType;
import com.training.platform.customers.dto.CustomerRequest;
import com.training.platform.customers.dto.CustomerResponse;
import com.training.platform.customers.entity.CustomerEntity;
import com.training.platform.customers.exception.BadRequestException;
import com.training.platform.customers.exception.DuplicateResourceException;
import com.training.platform.customers.exception.ResourceNotFoundException;
import com.training.platform.customers.mapper.CustomerMapper;
import com.training.platform.customers.repository.CustomerRepository;
import com.training.platform.customers.repository.KycRepository;
import com.training.platform.customers.service.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customerRepository;
    private final KycRepository kycRepository;
    private final CustomerMapper customerMapper;

    @Override
    public CustomerResponse createCustomer(CustomerRequest request) {
        validateCreateRequest(request);

        if (customerRepository.existsByPhone(request.phone())) {
            throw new DuplicateResourceException("Phone number already exists: " + request.phone());
        }

        if (request.email() != null && !request.email().isBlank()
                && customerRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("Email already exists: " + request.email());
        }

        CustomerEntity customerEntity = customerMapper.toEntity(request);
        customerEntity.setCustomerId(null);
        customerEntity.setCifNo(generateUniqueCif());
        customerEntity.setStatus(CustomerStatus.NEW);

        CustomerEntity saved = customerRepository.save(customerEntity);
        return customerMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerResponse getCustomerById(Long customerId) {
        CustomerEntity customer = customerRepository.findById(customerId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Customer not found with id: " + customerId));
        return customerMapper.toResponse(customer);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerResponse> getAllCustomers() {
        return customerRepository.findAll()
                .stream()
                .map(customerMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public CustomerResponse updateCustomer(Long customerId, CustomerRequest request) {
        if (request == null) {
            throw new BadRequestException("Customer request cannot be null");
        }

        CustomerEntity existing = customerRepository.findById(customerId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Customer not found with id: " + customerId));

        if (request.phone() != null
                && !request.phone().equals(existing.getPhone())
                && customerRepository.existsByPhone(request.phone())) {
            throw new DuplicateResourceException("Phone number already exists: " + request.phone());
        }

        if (request.email() != null
                && !request.email().isBlank()
                && !request.email().equals(existing.getEmail())
                && customerRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("Email already exists: " + request.email());
        }

        customerMapper.updateEntity(existing, request);

        CustomerEntity saved = customerRepository.save(existing);
        return customerMapper.toResponse(saved);
    }

    @Override
    public void deleteCustomer(Long customerId) {
        CustomerEntity customer = customerRepository.findById(customerId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Customer not found with id: " + customerId));

        customer.setStatus(CustomerStatus.INACTIVE);
        customerRepository.save(customer);
    }

    @Override
    public CustomerResponse activateCustomer(Long customerId) {
        CustomerEntity customer = customerRepository.findById(customerId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Customer not found with id: " + customerId));

        boolean kycVerified = kycRepository.findByCustomerCustomerId(customerId)
                .map(kyc -> kyc.getKycStatus() == KycStatusType.VERIFIED)
                .orElse(false);
        if (!kycVerified) {
            throw new BadRequestException("Customer cannot be activated until KYC is verified");
        }

        customer.setStatus(CustomerStatus.ACTIVE);
        return customerMapper.toResponse(customerRepository.save(customer));
    }

    @Override
    public CustomerResponse deactivateCustomer(Long customerId) {
        CustomerEntity customer = customerRepository.findById(customerId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Customer not found with id: " + customerId));

        customer.setStatus(CustomerStatus.INACTIVE);
        return customerMapper.toResponse(customerRepository.save(customer));
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerResponse getCustomerByCifNo(String cifNo) {
        CustomerEntity customer = customerRepository.findByCifNo(cifNo)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Customer not found with CIF: " + cifNo));
        return customerMapper.toResponse(customer);
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerResponse getCustomerByEmail(String email) {
        CustomerEntity customer = customerRepository.findByEmail(email)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Customer not found with email: " + email));
        return customerMapper.toResponse(customer);
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerResponse getCustomerByPhone(String phone) {
        CustomerEntity customer = customerRepository.findByPhone(phone)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Customer not found with phone: " + phone));
        return customerMapper.toResponse(customer);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerResponse> getCustomersByStatus(CustomerStatus status) {
        return customerRepository.findByStatus(status)
                .stream()
                .map(customerMapper::toResponse)
                .collect(Collectors.toList());
    }

    private void validateCreateRequest(CustomerRequest request) {
        if (request == null) {
            throw new BadRequestException("Customer request cannot be null");
        }
        if (request.firstName() == null || request.firstName().isBlank()) {
            throw new BadRequestException("First name is required");
        }
        if (request.dob() == null) {
            throw new BadRequestException("Date of birth is required");
        }
        if (request.gender() == null) {
            throw new BadRequestException("Gender is required");
        }
        if (request.phone() == null || request.phone().isBlank()) {
            throw new BadRequestException("Phone number is required");
        }
    }

    private String generateUniqueCif() {
        String cif;
        do {
            cif = "CIF" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        } while (customerRepository.existsByCifNo(cif));
        return cif;
    }
}
