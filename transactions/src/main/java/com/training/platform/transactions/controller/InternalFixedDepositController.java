package com.training.platform.transactions.controller;

import com.training.platform.transactions.dto.FixedDepositDependencyResponse;
import com.training.platform.transactions.entity.FixedDepositStatus;
import com.training.platform.transactions.repository.FixedDepositContractRepository;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/fixed-deposits")
@PreAuthorize("hasAnyRole('SYSTEM','ADMIN')")
public class InternalFixedDepositController {
    private static final List<FixedDepositStatus> OPEN_STATUSES = List.of(
            FixedDepositStatus.PENDING_FUNDING,
            FixedDepositStatus.FUNDING_FAILED,
            FixedDepositStatus.ACTIVE);

    private final FixedDepositContractRepository repository;

    public InternalFixedDepositController(FixedDepositContractRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/dependencies")
    public List<FixedDepositDependencyResponse> dependencies(@RequestParam String accountId) {
        return repository.findDependencies(accountId, OPEN_STATUSES).stream()
                .map(FixedDepositDependencyResponse::from)
                .toList();
    }
}
