package com.training.platform.customers.service.impl;

import com.training.platform.auditclient.AuditClient;
import com.training.platform.customers.dto.AddressRequest;
import com.training.platform.customers.dto.AddressResponse;
import com.training.platform.customers.entity.AddressEntity;
import com.training.platform.customers.entity.CustomerEntity;
import com.training.platform.customers.exception.BadRequestException;
import com.training.platform.customers.exception.ResourceNotFoundException;
import com.training.platform.customers.mapper.AddressMapper;
import com.training.platform.customers.repository.AddressRepository;
import com.training.platform.customers.repository.CustomerRepository;
import com.training.platform.customers.service.AddressService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class AddressServiceImpl implements AddressService {

    private final AddressRepository addressRepository;
    private final CustomerRepository customerRepository;
    private final AddressMapper addressMapper;
    private final AuditClient auditClient;

    @Override
    public AddressResponse addAddress(Long customerId, AddressRequest request) {
        validateAddressRequest(request);

        CustomerEntity customer = customerRepository.findById(customerId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Customer not found with id: " + customerId));

        AddressEntity addressEntity = addressMapper.toEntity(request);
        addressEntity.setAddressId(null);
        addressEntity.setCustomer(customer);

        AddressEntity saved = addressRepository.save(addressEntity);
        auditAddressChange(customerId, saved, "ADDRESS_ADDED", "Customer address added",
                Map.of(), addressValues(saved));
        return addressMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public AddressResponse getAddressById(Long customerId, Long addressId) {
        AddressEntity address = addressRepository.findByAddressIdAndCustomerCustomerId(addressId, customerId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Address not found with id: " + addressId + " for customer: " + customerId
                        ));
        return addressMapper.toResponse(address);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AddressResponse> getAddressesByCustomerId(Long customerId) {
        return addressRepository.findByCustomerCustomerId(customerId)
                .stream()
                .map(addressMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public AddressResponse updateAddress(Long customerId, Long addressId, AddressRequest request) {
        validateAddressRequest(request);

        AddressEntity existing = addressRepository.findByAddressIdAndCustomerCustomerId(addressId, customerId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Address not found with id: " + addressId + " for customer: " + customerId
                        ));

        Map<String, Object> previousValues = addressValues(existing);
        addressMapper.updateEntity(existing, request);

        AddressEntity saved = addressRepository.save(existing);
        auditAddressChange(customerId, saved, "ADDRESS_UPDATED", "Address fields changed",
                previousValues, addressValues(saved));
        return addressMapper.toResponse(saved);
    }

    @Override
    public void deleteAddress(Long customerId, Long addressId) {
        AddressEntity existing = addressRepository.findByAddressIdAndCustomerCustomerId(addressId, customerId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Address not found with id: " + addressId + " for customer: " + customerId
                        ));

        Map<String, Object> previousValues = addressValues(existing);
        addressRepository.delete(existing);
        auditAddressChange(customerId, existing, "ADDRESS_DELETED", "Customer address deleted",
                previousValues, Map.of());
    }

    private void validateAddressRequest(AddressRequest request) {
        if (request == null) {
            throw new BadRequestException("Address request cannot be null");
        }
        if (request.addressType() == null) {
            throw new BadRequestException("Address type is required");
        }
        if (request.line1() == null || request.line1().isBlank()) {
            throw new BadRequestException("Line1 is required");
        }
        if (request.city() == null || request.city().isBlank()) {
            throw new BadRequestException("City is required");
        }
        if (request.state() == null || request.state().isBlank()) {
            throw new BadRequestException("State is required");
        }
        if (request.country() == null || request.country().isBlank()) {
            throw new BadRequestException("Country is required");
        }
        if (request.pincode() == null || request.pincode().isBlank()) {
            throw new BadRequestException("Pincode is required");
        }
    }

    private Map<String, Object> addressValues(AddressEntity address) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("addressType", address.getAddressType() == null ? null : address.getAddressType().name());
        values.put("line1", address.getLine1());
        values.put("line2", address.getLine2());
        values.put("city", address.getCity());
        values.put("state", address.getState());
        values.put("country", address.getCountry());
        values.put("pincode", address.getPincode());
        return values;
    }

    private void auditAddressChange(Long customerId, AddressEntity address, String action, String description,
                                    Map<String, ?> previousValues, Map<String, ?> newValues) {
        Map<String, Object> changes = auditClient.changes(previousValues, newValues);
        if (changes != null && changes.isEmpty()) return;
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("customerId", customerId);
        details.put("relatedEntityType", "ADDRESS");
        details.put("relatedEntityId", address.getAddressId().toString());
        if (changes != null) {
            details.putAll(changes);
            if (!previousValues.isEmpty() && !newValues.isEmpty()) description += ": " + changes.get("changedFields");
        }
        auditClient.success("customers", action, description, details);
    }
}
