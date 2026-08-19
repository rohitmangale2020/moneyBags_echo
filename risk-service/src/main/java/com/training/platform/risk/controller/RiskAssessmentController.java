package com.training.platform.risk.controller;

import com.training.platform.risk.dto.ApprovedTransactionProfileRequest;
import com.training.platform.risk.dto.RiskAssessmentRequest;
import com.training.platform.risk.dto.RiskAssessmentResponse;
import com.training.platform.risk.service.RiskAssessmentService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/risk")
public class RiskAssessmentController {
    private final RiskAssessmentService service;

    public RiskAssessmentController(RiskAssessmentService service) { this.service = service; }

    /** Scores a transaction before any account posting occurs. This endpoint never updates profiles. */
    @PostMapping("/assessments")
    @ResponseStatus(HttpStatus.CREATED)
    public RiskAssessmentResponse assess(@Valid @RequestBody RiskAssessmentRequest request) {
        return RiskAssessmentResponse.from(service.assess(request));
    }

    /** Called only after an approved transaction has completed; this is the sole profile update path. */
    @PostMapping("/profiles/approved-transactions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void recordApproved(@Valid @RequestBody ApprovedTransactionProfileRequest request) {
        service.recordApprovedTransaction(request);
    }

    @GetMapping("/assessments/transaction/{transactionRef}")
    public List<RiskAssessmentResponse> assessmentsFor(@PathVariable String transactionRef) {
        return service.assessmentsFor(transactionRef).stream().map(RiskAssessmentResponse::from).toList();
    }
}
