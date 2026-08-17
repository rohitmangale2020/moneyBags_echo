package com.training.platform.transactions.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class DepositInterestCalculationTest {
    @Test void savingsInterestUsesAnnualRateAndActualDays() {
        BigDecimal result = SavingsInterestService.interest(new BigDecimal("100000.00"),
                new BigDecimal("4.00"), LocalDate.of(2026, 7, 31), LocalDate.of(2026, 8, 31));
        assertEquals(new BigDecimal("339.73"), result);
    }

    @Test void fixedDepositInterestUsesContractRateSnapshot() {
        BigDecimal result = FixedDepositService.interest(new BigDecimal("100000.00"),
                new BigDecimal("6.50"), LocalDate.of(2025, 8, 16), LocalDate.of(2026, 8, 16));
        assertEquals(new BigDecimal("6500.00"), result);
    }
}
