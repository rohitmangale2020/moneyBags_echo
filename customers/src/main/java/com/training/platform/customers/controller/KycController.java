package com.training.platform.customers.controller;

import com.training.platform.customers.dto.KycRequest;
import com.training.platform.customers.dto.KycResponse;
import com.training.platform.customers.service.KycService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customers/{customerId}/kyc")
@RequiredArgsConstructor
public class KycController {

    private final KycService kycService;

    @PostMapping
    public ResponseEntity<KycResponse> createKyc(
            @PathVariable Long customerId,
            @Valid @RequestBody KycRequest requestDto
    ) {
        return new ResponseEntity<>(kycService.createKyc(customerId, requestDto), HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<KycResponse> getKycByCustomerId(
            @PathVariable Long customerId
    ) {
        return ResponseEntity.ok(kycService.getKycByCustomerId(customerId));
    }

    @PutMapping
    public ResponseEntity<KycResponse> updateKyc(
            @PathVariable Long customerId,
            @Valid @RequestBody KycRequest requestDto
    ) {
        return ResponseEntity.ok(kycService.updateKyc(customerId, requestDto));
    }
}