package com.training.platform.transactions.service;

import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DepositProcessingScheduler {
    private static final Logger log = LoggerFactory.getLogger(DepositProcessingScheduler.class);
    private final SavingsInterestService savingsInterestService;
    private final FixedDepositMaturityProcessor maturityProcessor;
    private final AnnualMaintenanceFeeService maintenanceFeeService;
    private final boolean interestEnabled;
    private final boolean maturityEnabled;
    private final boolean maintenanceFeeEnabled;

    public DepositProcessingScheduler(SavingsInterestService savingsInterestService,
                                      FixedDepositMaturityProcessor maturityProcessor,
                                      AnnualMaintenanceFeeService maintenanceFeeService,
                                      @Value("${banking.interest.scheduler-enabled:true}") boolean interestEnabled,
                                      @Value("${banking.fixed-deposit.scheduler-enabled:true}") boolean maturityEnabled,
                                      @Value("${banking.annual-fee.scheduler-enabled:true}") boolean maintenanceFeeEnabled) {
        this.savingsInterestService = savingsInterestService;
        this.maturityProcessor = maturityProcessor;
        this.maintenanceFeeService = maintenanceFeeService;
        this.interestEnabled = interestEnabled;
        this.maturityEnabled = maturityEnabled;
        this.maintenanceFeeEnabled = maintenanceFeeEnabled;
    }

    @Scheduled(cron = "${banking.interest.cron:0 15 1 1 * *}")
    public void monthlyInterest() {
        if (!interestEnabled) return;
        LocalDate periodEnd = LocalDate.now().minusDays(1);
        try {
            log.info("Processed interest for {} accounts through {}",
                    savingsInterestService.processDue(periodEnd), periodEnd);
        } catch (RuntimeException exception) {
            log.error("Monthly interest processing failed", exception);
        }
    }

    /** Daily anniversary scan; deterministic yearly references prevent duplicate fees. */
    @Scheduled(cron = "${banking.annual-fee.cron:0 0 2 * * *}")
    public void annualMaintenanceFees() {
        if (!maintenanceFeeEnabled) return;
        LocalDate asOf = LocalDate.now();
        try {
            log.info("Processed annual maintenance fees for {} accounts on {}",
                    maintenanceFeeService.process(asOf), asOf);
        } catch (RuntimeException exception) {
            log.error("Annual maintenance-fee processing failed", exception);
        }
    }

    @Scheduled(cron = "${banking.fixed-deposit.cron:0 30 1 * * *}")
    public void fixedDepositMaturities() {
        if (!maturityEnabled) return;
        LocalDate asOf = LocalDate.now();
        try {
            log.info("Matured {} fixed deposits through {}",
                    maturityProcessor.process(asOf), asOf);
        } catch (RuntimeException exception) {
            log.error("Fixed-deposit maturity processing failed", exception);
        }
    }
}
