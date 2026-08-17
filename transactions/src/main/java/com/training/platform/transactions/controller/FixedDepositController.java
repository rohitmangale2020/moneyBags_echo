package com.training.platform.transactions.controller;

import com.training.platform.transactions.dto.FixedDepositOpenRequest;
import com.training.platform.transactions.dto.FixedDepositResponse;
import com.training.platform.transactions.service.FixedDepositService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/fixed-deposits")
public class FixedDepositController {
    private final FixedDepositService service;

    public FixedDepositController(FixedDepositService service) { this.service = service; }

    @GetMapping public List<FixedDepositResponse> all() {
        return service.all().stream().map(FixedDepositResponse::from).toList();
    }

    @GetMapping("/{contractId}") public FixedDepositResponse get(@PathVariable String contractId) {
        return FixedDepositResponse.from(service.get(contractId));
    }

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public FixedDepositResponse open(@Valid @RequestBody FixedDepositOpenRequest request) {
        return FixedDepositResponse.from(service.open(request));
    }

    @PostMapping("/{contractId}/retry-funding")
    public FixedDepositResponse retryFunding(@PathVariable String contractId) {
        return FixedDepositResponse.from(service.retryFunding(contractId));
    }

    @PostMapping("/{contractId}/close")
    public FixedDepositResponse closePrematurely(
            @PathVariable String contractId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return FixedDepositResponse.from(service.closePrematurely(contractId,
                asOf == null ? LocalDate.now() : asOf));
    }
}
