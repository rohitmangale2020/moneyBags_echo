package com.training.platform.customers.service;

import com.training.platform.customers.constants.CustomerStatus;
import com.training.platform.customers.dto.CustomerRequest;
import com.training.platform.customers.dto.CustomerResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface CustomerService {

    CustomerResponse createCustomer(CustomerRequest request);

    CustomerResponse getCustomerById(Long customerId);

    Page<CustomerResponse> getAllCustomers(Pageable pageable, CustomerStatus status);

    CustomerResponse updateCustomer(Long customerId, CustomerRequest request);

    void deleteCustomer(Long customerId);

    CustomerResponse activateCustomer(Long customerId);

    CustomerResponse deactivateCustomer(Long customerId);

    CustomerResponse getCustomerByCifNo(String cifNo);

    CustomerResponse getCustomerByEmail(String email);

    CustomerResponse getCustomerByPhone(String phone);

    List<CustomerResponse> getCustomersByFirstName(String firstName);

    List<CustomerResponse> getCustomersByStatus(CustomerStatus status);
}
