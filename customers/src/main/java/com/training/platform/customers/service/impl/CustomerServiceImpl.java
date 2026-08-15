package com.training.platform.customers.service.impl;

import com.training.platform.auditclient.AuditClient;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customerRepository;
    private final KycRepository kycRepository;
    private final CustomerMapper customerMapper;
    private final AuditClient auditClient;

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
        auditCustomerChange(saved, "CUSTOMER_CREATED", "Customer profile created",
                Map.of(), customerValues(saved));
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
    public Page<CustomerResponse> getAllCustomers(Pageable pageable, CustomerStatus status) {
        Page<CustomerEntity> customers = status == null
                ? customerRepository.findAll(pageable)
                : customerRepository.findByStatus(status, pageable);
        return customers
                .map(customerMapper::toResponse);
    }

    @Override
    public CustomerResponse updateCustomer(Long customerId, CustomerRequest request) {
        if (request == null) {
            throw new BadRequestException("Customer request cannot be null");
        }

        CustomerEntity existing = customerRepository.findById(customerId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Customer not found with id: " + customerId));
        Map<String, Object> previousValues = customerValues(existing);

        if (request.dob() != null) {
            validateAdultDateOfBirth(request.dob());
        }

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
        auditCustomerChange(saved, "CUSTOMER_UPDATED", "Customer fields changed",
                previousValues, customerValues(saved));
        return customerMapper.toResponse(saved);
    }

    @Override
    public void deleteCustomer(Long customerId) {
        CustomerEntity customer = customerRepository.findById(customerId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Customer not found with id: " + customerId));

        String previousStatus = customer.getStatus().name();
        customer.setStatus(CustomerStatus.INACTIVE);
        customerRepository.save(customer);
        auditStatusChange(customer, "CUSTOMER_DEACTIVATED", "Customer deactivated",
                previousStatus, CustomerStatus.INACTIVE.name());
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

        String previousStatus = customer.getStatus().name();
        customer.setStatus(CustomerStatus.ACTIVE);
        CustomerEntity saved = customerRepository.save(customer);
        auditStatusChange(saved, "CUSTOMER_ACTIVATED", "Customer activated",
                previousStatus, CustomerStatus.ACTIVE.name());
        return customerMapper.toResponse(saved);
    }

    @Override
    public CustomerResponse deactivateCustomer(Long customerId) {
        CustomerEntity customer = customerRepository.findById(customerId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Customer not found with id: " + customerId));

        String previousStatus = customer.getStatus().name();
        customer.setStatus(CustomerStatus.INACTIVE);
        CustomerEntity saved = customerRepository.save(customer);
        auditStatusChange(saved, "CUSTOMER_DEACTIVATED", "Customer deactivated",
                previousStatus, CustomerStatus.INACTIVE.name());
        return customerMapper.toResponse(saved);
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

    @Override
    @Transactional(readOnly = true)
    public List<CustomerResponse> getCustomersByFirstName(String firstName) {
        return customerRepository.findByFirstNameContainingIgnoreCase(firstName)
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
        validateAdultDateOfBirth(request.dob());
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

    private void validateAdultDateOfBirth(LocalDate dob) {
        if (!dob.plusYears(18).isBefore(LocalDate.now().plusDays(1))) {
            throw new BadRequestException("Customer must be at least 18 years old.");
        }
    }

    private Map<String, Object> customerValues(CustomerEntity customer) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("cifNo", customer.getCifNo());
        values.put("firstName", customer.getFirstName());
        values.put("lastName", customer.getLastName());
        values.put("dob", customer.getDob());
        values.put("gender", customer.getGender() == null ? null : customer.getGender().name());
        values.put("phone", customer.getPhone());
        values.put("email", customer.getEmail());
        values.put("occupation", customer.getOccupation());
        values.put("status", customer.getStatus() == null ? null : customer.getStatus().name());
        return values;
    }

    private void auditCustomerChange(CustomerEntity customer, String action, String description,
                                     Map<String, ?> previousValues, Map<String, ?> newValues) {
        Map<String, Object> changes = auditClient.changes(previousValues, newValues);
        if (changes != null && changes.isEmpty()) return;
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("customerId", customer.getCustomerId());
        details.put("newStatus", customer.getStatus() == null ? null : customer.getStatus().name());
        if (changes != null) {
            details.putAll(changes);
            if (!previousValues.isEmpty()) description += ": " + changes.get("changedFields");
        }
        auditClient.success("customers", action, description, details);
    }

    private void auditStatusChange(CustomerEntity customer, String action, String description,
                                   String previousStatus, String newStatus) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("customerId", customer.getCustomerId());
        details.put("previousStatus", previousStatus);
        details.put("newStatus", newStatus);
        Map<String, Object> changes = auditClient.changes(
                Map.of("status", previousStatus), Map.of("status", newStatus));
        if (changes != null) details.putAll(changes);
        auditClient.success("customers", action, description, details);
    }
}
