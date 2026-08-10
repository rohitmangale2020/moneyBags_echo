package com.training.platform.customers.service;

import com.training.platform.customers.constants.CustomerStatus;
import com.training.platform.customers.dto.CustomerRequest;
import com.training.platform.customers.dto.CustomerResponse;

import java.util.List;

public interface CustomerService {

    CustomerResponse createCustomer(CustomerRequest request);

    CustomerResponse getCustomerById(Long customerId);

    List<CustomerResponse> getAllCustomers();

    CustomerResponse updateCustomer(Long customerId, CustomerRequest request);

    void deleteCustomer(Long customerId);

    CustomerResponse activateCustomer(Long customerId);

    CustomerResponse deactivateCustomer(Long customerId);

    CustomerResponse getCustomerByCifNo(String cifNo);

    CustomerResponse getCustomerByEmail(String email);

    CustomerResponse getCustomerByPhone(String phone);

    List<CustomerResponse> getCustomersByStatus(CustomerStatus status);
}