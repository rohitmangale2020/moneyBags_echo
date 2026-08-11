package com.training.platform.customers.controller;

import com.training.platform.customers.constants.CustomerStatus;
import com.training.platform.customers.dto.CustomerRequest;
import com.training.platform.customers.dto.CustomerResponse;
import com.training.platform.customers.service.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @PostMapping
    public ResponseEntity<CustomerResponse> createCustomer(@Valid @RequestBody CustomerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(customerService.createCustomer(request));
    }

    @GetMapping("/{customerId}")
    public ResponseEntity<CustomerResponse> getCustomerById(@PathVariable Long customerId) {
        return ResponseEntity.ok(customerService.getCustomerById(customerId));
    }

    @GetMapping
    public ResponseEntity<List<CustomerResponse>> getAllCustomers() {
        return ResponseEntity.ok(customerService.getAllCustomers());
    }

    @PutMapping("/{customerId}")
    public ResponseEntity<CustomerResponse> updateCustomer(
            @PathVariable Long customerId,
            @Valid @RequestBody CustomerRequest request) {
        return ResponseEntity.ok(customerService.updateCustomer(customerId, request));
    }

    @DeleteMapping("/{customerId}")
    public ResponseEntity<Void> deleteCustomer(@PathVariable Long customerId) {
        customerService.deleteCustomer(customerId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{customerId}/activate")
    public ResponseEntity<CustomerResponse> activateCustomer(@PathVariable Long customerId) {
        return ResponseEntity.ok(customerService.activateCustomer(customerId));
    }

    @PatchMapping("/{customerId}/deactivate")
    public ResponseEntity<CustomerResponse> deactivateCustomer(@PathVariable Long customerId) {
        return ResponseEntity.ok(customerService.deactivateCustomer(customerId));
    }

    @GetMapping("/search/cif/{cifNo}")
    public ResponseEntity<CustomerResponse> getByCifNo(@PathVariable String cifNo) {
        return ResponseEntity.ok(customerService.getCustomerByCifNo(cifNo));
    }

    @GetMapping("/search/email/{email}")
    public ResponseEntity<CustomerResponse> getByEmail(@PathVariable String email) {
        return ResponseEntity.ok(customerService.getCustomerByEmail(email));
    }

    @GetMapping("/search/phone/{phone}")
    public ResponseEntity<CustomerResponse> getByPhone(@PathVariable String phone) {
        return ResponseEntity.ok(customerService.getCustomerByPhone(phone));
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<CustomerResponse>> getByStatus(@PathVariable CustomerStatus status) {
        return ResponseEntity.ok(customerService.getCustomersByStatus(status));
    }
}
