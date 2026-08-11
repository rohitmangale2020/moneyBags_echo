package com.bank.product.api;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
public record FeeRequest(@NotNull @DecimalMin("0.00") BigDecimal monthlyMaintenanceFee) { }
