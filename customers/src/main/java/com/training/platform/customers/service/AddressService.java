package com.training.platform.customers.service;

import com.training.platform.customers.dto.AddressRequest;
import com.training.platform.customers.dto.AddressResponse;

import java.util.List;

public interface AddressService {

    AddressResponse addAddress(Long customerId, AddressRequest request);

    AddressResponse getAddressById(Long customerId, Long addressId);

    List<AddressResponse> getAddressesByCustomerId(Long customerId);

    AddressResponse updateAddress(Long customerId, Long addressId, AddressRequest request);

    void deleteAddress(Long customerId, Long addressId);
}