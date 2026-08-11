package com.bank.product.api;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;
public record TermRequest(Integer tenureMonths, @DecimalMin("0.00") BigDecimal installmentAmount,
                          String installmentFrequency, Integer lockInPeriod, String maturityInstruction,
                          Boolean prematureWithdrawalAllowed) { }
