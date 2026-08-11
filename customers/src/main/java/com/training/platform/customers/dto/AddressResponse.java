package com.training.platform.customers.dto;

import com.training.platform.customers.constants.AddressType;

public record AddressResponse(
        Long addressId,
        Long customerId,
        AddressType addressType,
        String line1,
        String line2,
        String city,
        String state,
        String country,
        String pincode
) {
}