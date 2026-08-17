package com.training.platform.transactions.dto;

import com.training.platform.transactions.entity.FixedDepositContract;
import com.training.platform.transactions.entity.FixedDepositStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record FixedDepositResponse(
        String contractId,
        String fdAccountId,
        String fundingAccountId,
        String payoutAccountId,
        String customerId,
        String productId,
        BigDecimal principal,
        BigDecimal annualInterestRate,
        Integer tenureMonths,
        LocalDate openedOn,
        LocalDate lockInUntil,
        LocalDate maturityDate,
        FixedDepositStatus status,
        BigDecimal interestPaid,
        String fundingTransactionId,
        String closureTransactionId,
        String interestTransactionId,
        LocalDateTime closedAt) {

    public static FixedDepositResponse from(FixedDepositContract contract) {
        return new FixedDepositResponse(contract.getContractId(), contract.getFdAccountId(),
                contract.getFundingAccountId(), contract.getPayoutAccountId(), contract.getCustomerId(),
                contract.getProductId(), contract.getPrincipal(), contract.getAnnualInterestRate(),
                contract.getTenureMonths(), contract.getOpenedOn(), contract.getLockInUntil(),
                contract.getMaturityDate(), contract.getStatus(), contract.getInterestPaid(),
                contract.getFundingTransactionId(), contract.getClosureTransactionId(),
                contract.getInterestTransactionId(), contract.getClosedAt());
    }
}
