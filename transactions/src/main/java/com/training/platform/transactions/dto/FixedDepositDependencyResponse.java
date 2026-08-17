package com.training.platform.transactions.dto;

import com.training.platform.transactions.entity.FixedDepositContract;

public record FixedDepositDependencyResponse(
        String contractId,
        String fdAccountId,
        String fundingAccountId,
        String payoutAccountId,
        String status) {

    public static FixedDepositDependencyResponse from(FixedDepositContract contract) {
        return new FixedDepositDependencyResponse(contract.getContractId(), contract.getFdAccountId(),
                contract.getFundingAccountId(), contract.getPayoutAccountId(), contract.getStatus().name());
    }
}
