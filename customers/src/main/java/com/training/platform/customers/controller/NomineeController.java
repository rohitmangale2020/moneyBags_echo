package com.training.platform.customers.controller;

import com.training.platform.customers.dto.NomineeRequestDto;
import com.training.platform.customers.dto.NomineeResponseDto;
import com.training.platform.customers.service.NomineeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customers/{customerId}/nominees")
@RequiredArgsConstructor
public class NomineeController {

    private final NomineeService nomineeService;

    @PostMapping
    public ResponseEntity<NomineeResponseDto> createNominee(
            @PathVariable Long customerId,
            @Valid @RequestBody NomineeRequestDto requestDto
    ) {
        return new ResponseEntity<>(nomineeService.createNominee(customerId, requestDto), HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<NomineeResponseDto>> getAllNominees(
            @PathVariable Long customerId
    ) {
        return ResponseEntity.ok(nomineeService.getAllNomineesByCustomerId(customerId));
    }

    @GetMapping("/{nomineeId}")
    public ResponseEntity<NomineeResponseDto> getNomineeById(
            @PathVariable Long customerId,
            @PathVariable Long nomineeId
    ) {
        return ResponseEntity.ok(nomineeService.getNomineeById(customerId, nomineeId));
    }

    @PutMapping("/{nomineeId}")
    public ResponseEntity<NomineeResponseDto> updateNominee(
            @PathVariable Long customerId,
            @PathVariable Long nomineeId,
            @Valid @RequestBody NomineeRequestDto requestDto
    ) {
        return ResponseEntity.ok(nomineeService.updateNominee(customerId, nomineeId, requestDto));
    }

    @PatchMapping("/{nomineeId}/close")
    public ResponseEntity<NomineeResponseDto> closeNominee(
            @PathVariable Long customerId,
            @PathVariable Long nomineeId
    ) {
        return ResponseEntity.ok(nomineeService.closeNominee(customerId, nomineeId));
    }

    @DeleteMapping("/{nomineeId}")
    public ResponseEntity<Void> deleteNominee(
            @PathVariable Long customerId,
            @PathVariable Long nomineeId
    ) {
        nomineeService.deleteNominee(customerId, nomineeId);
        return ResponseEntity.noContent().build();
    }
}
