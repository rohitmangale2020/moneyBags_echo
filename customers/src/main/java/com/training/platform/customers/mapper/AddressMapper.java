package com.training.platform.customers.mapper;

import com.training.platform.customers.dto.AddressRequest;
import com.training.platform.customers.dto.AddressResponse;
import com.training.platform.customers.entity.AddressEntity;
import org.springframework.stereotype.Component;

@Component
public class AddressMapper {

    public AddressEntity toEntity(AddressRequest request) {
        if (request == null) {
            return null;
        }

        return AddressEntity.builder()
                .addressType(request.addressType())
                .line1(request.line1())
                .line2(request.line2())
                .city(request.city())
                .state(request.state())
                .country(request.country())
                .pincode(request.pincode())
                .build();
    }

    public AddressResponse toResponse(AddressEntity entity) {
        if (entity == null) {
            return null;
        }

        return new AddressResponse(
                entity.getAddressId(),
                entity.getCustomer() != null ? entity.getCustomer().getCustomerId() : null,
                entity.getAddressType(),
                entity.getLine1(),
                entity.getLine2(),
                entity.getCity(),
                entity.getState(),
                entity.getCountry(),
                entity.getPincode()
        );
    }

    public void updateEntity(AddressEntity entity, AddressRequest request) {
        if (entity == null || request == null) {
            return;
        }

        if (request.addressType() != null) {
            entity.setAddressType(request.addressType());
        }
        if (request.line1() != null) {
            entity.setLine1(request.line1());
        }
        if (request.line2() != null) {
            entity.setLine2(request.line2());
        }
        if (request.city() != null) {
            entity.setCity(request.city());
        }
        if (request.state() != null) {
            entity.setState(request.state());
        }
        if (request.country() != null) {
            entity.setCountry(request.country());
        }
        if (request.pincode() != null) {
            entity.setPincode(request.pincode());
        }
    }
}