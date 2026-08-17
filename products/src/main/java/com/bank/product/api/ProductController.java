package com.bank.product.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/v1/products") @RequiredArgsConstructor
public class ProductController {
    private final ProductService service;
    @PostMapping @PreAuthorize("hasRole('ADMIN')") public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest r) { return ResponseEntity.status(HttpStatus.CREATED).body(service.create(r)); }
    @GetMapping public List<ProductResponse> findAll(Authentication authentication) { return service.findAll(authentication); }
    @GetMapping("/id/{productId}") public ProductResponse findById(@PathVariable Long productId, Authentication authentication) { return service.findById(productId, authentication); }
    @GetMapping("/{productCode}") public ProductResponse findOne(@PathVariable String productCode, Authentication authentication) { return service.findByCode(productCode, authentication); }
    @GetMapping("/{productCode}/status-history") public List<ProductStatusHistoryResponse> statusHistory(@PathVariable String productCode) { return service.statusHistory(productCode); }
    @GetMapping("/{productCode}/retirement-impact") @PreAuthorize("hasRole('ADMIN')") public ProductRetirementImpactResponse retirementImpact(@PathVariable String productCode) { return service.retirementImpact(productCode); }
    @PutMapping("/{productCode}") @PreAuthorize("hasRole('ADMIN')") public ProductResponse update(@PathVariable String productCode, @Valid @RequestBody ProductRequest r) { return service.update(productCode, r); }
    @PatchMapping("/{productCode}/status") @PreAuthorize("hasRole('EMPLOYEE')") public ProductResponse status(@PathVariable String productCode, @Valid @RequestBody StatusRequest r) { return service.updateStatus(productCode, r); }
    @PostMapping("/{productCode}/retire") @PreAuthorize("hasRole('ADMIN')") @ResponseStatus(HttpStatus.NO_CONTENT) public void retire(@PathVariable String productCode, @RequestBody(required = false) RetirementRequest r) { service.retire(productCode, r == null ? null : r.migrationProductCode()); }
    @DeleteMapping("/{productCode}") @PreAuthorize("hasRole('ADMIN')") @ResponseStatus(HttpStatus.NO_CONTENT) public void retireLegacy(@PathVariable String productCode) { service.retire(productCode, null); }
}
