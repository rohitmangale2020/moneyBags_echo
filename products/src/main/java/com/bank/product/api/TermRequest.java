package com.bank.product.api;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import java.math.BigDecimal;
public record TermRequest(Integer tenureMonths, @DecimalMin("0.00") @DecimalMax("999999999.99") @Digits(integer = 9, fraction = 2) BigDecimal installmentAmount,
                          String installmentFrequency, Integer lockInPeriod, String maturityInstruction,
                          Boolean prematureWithdrawalAllowed) { }
