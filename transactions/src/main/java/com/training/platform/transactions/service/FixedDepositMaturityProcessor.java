package com.training.platform.transactions.service;

import com.training.platform.transactions.entity.FixedDepositContract;
import com.training.platform.transactions.entity.FixedDepositStatus;
import com.training.platform.transactions.repository.FixedDepositContractRepository;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Runs every due contract in its own transaction so one failure cannot roll back other maturities. */
@Service
public class FixedDepositMaturityProcessor {
    private static final Logger log = LoggerFactory.getLogger(FixedDepositMaturityProcessor.class);
    private final FixedDepositContractRepository repository;
    private final FixedDepositService fixedDepositService;

    public FixedDepositMaturityProcessor(FixedDepositContractRepository repository,
                                         FixedDepositService fixedDepositService) {
        this.repository = repository;
        this.fixedDepositService = fixedDepositService;
    }

    public int process(LocalDate asOf) {
        List<String> dueIds = repository
                .findByStatusAndMaturityDateLessThanEqualOrderByMaturityDateAsc(
                        FixedDepositStatus.ACTIVE, asOf)
                .stream().map(FixedDepositContract::getContractId).toList();
        int completed = 0;
        for (String contractId : dueIds) {
            try {
                if (fixedDepositService.mature(contractId, asOf).getStatus() == FixedDepositStatus.MATURED) {
                    completed++;
                }
            } catch (RuntimeException exception) {
                log.error("Failed to mature fixed-deposit contract {}", contractId, exception);
            }
        }
        return completed;
    }
}
