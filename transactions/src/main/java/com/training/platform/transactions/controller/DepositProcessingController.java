package com.training.platform.transactions.controller;

import com.training.platform.transactions.service.FixedDepositMaturityProcessor;
import com.training.platform.transactions.service.SavingsInterestService;
import com.training.platform.transactions.service.AnnualMaintenanceFeeService;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/deposit-processing")
@PreAuthorize("hasRole('ADMIN')")
public class DepositProcessingController {
    private final SavingsInterestService interestService;
    private final FixedDepositMaturityProcessor maturityProcessor;
    private final AnnualMaintenanceFeeService maintenanceFeeService;

    public DepositProcessingController(SavingsInterestService interestService,
                                       FixedDepositMaturityProcessor maturityProcessor,
                                       AnnualMaintenanceFeeService maintenanceFeeService) {
        this.interestService = interestService;
        this.maturityProcessor = maturityProcessor;
        this.maintenanceFeeService = maintenanceFeeService;
    }

    @PostMapping("/interest")
    public Map<String, Object> processInterest(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return Map.of("asOf", asOf, "processedAccounts", interestService.processDue(asOf));
    }

    @PostMapping("/maturities")
    public Map<String, Object> processMaturities(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return Map.of("asOf", asOf, "maturedContracts", maturityProcessor.process(asOf));
    }

    @PostMapping("/annual-fees")
    public Map<String, Object> processAnnualFees(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return Map.of("asOf", asOf, "processedAccounts", maintenanceFeeService.process(asOf));
    }
}
