package com.training.platform.customers.mapper;

import com.training.platform.customers.dto.CustomerRequest;
import com.training.platform.customers.dto.CustomerResponse;
import com.training.platform.customers.entity.CustomerEntity;
import org.springframework.stereotype.Component;

@Component
public class CustomerMapper {

    public CustomerEntity toEntity(CustomerRequest request) {
        if (request == null) {
            return null;
        }

        return CustomerEntity.builder()
                .firstName(request.firstName())
                .lastName(request.lastName())
                .dob(request.dob())
                .gender(request.gender())
                .phone(request.phone())
                .email(request.email())
                .occupation(request.occupation())
                .build();
    }

    public CustomerResponse toResponse(CustomerEntity entity) {
        if (entity == null) {
            return null;
        }

        return new CustomerResponse(
                entity.getCustomerId(),
                entity.getCifNo(),
                entity.getFirstName(),
                entity.getLastName(),
                entity.getDob(),
                entity.getGender(),
                entity.getPhone(),
                entity.getEmail(),
                entity.getOccupation(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public void updateEntity(CustomerEntity entity, CustomerRequest request) {
        if (entity == null || request == null) {
            return;
        }

        if (request.firstName() != null) {
            entity.setFirstName(request.firstName());
        }
        if (request.lastName() != null) {
            entity.setLastName(request.lastName());
        }
        if (request.dob() != null) {
            entity.setDob(request.dob());
        }
        if (request.gender() != null) {
            entity.setGender(request.gender());
        }
        if (request.phone() != null) {
            entity.setPhone(request.phone());
        }
        if (request.email() != null) {
            entity.setEmail(request.email());
        }
        if (request.occupation() != null) {
            entity.setOccupation(request.occupation());
        }
    }
}