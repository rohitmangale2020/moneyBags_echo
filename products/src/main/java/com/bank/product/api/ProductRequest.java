package com.bank.product.api;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record ProductRequest(
    @NotBlank @Size(max = 50) @Pattern(regexp = "[A-Za-z0-9_-]+") String productCode,
    @NotBlank @Size(max = 150) String productName,
    @NotBlank @Size(max = 30) String productTypeCode,
    @Size(max = 500) String description,
    @DecimalMin(value = "0.00", inclusive = true) @DecimalMax("999999999.99") @Digits(integer = 9, fraction = 2) BigDecimal minimumBalance,
    @DecimalMin(value = "0.00", inclusive = true) @DecimalMax("999999999.99") @Digits(integer = 9, fraction = 2) BigDecimal maximumBalance,
    @NotBlank @Pattern(regexp = "INR") String currency,
    @NotBlank @Pattern(regexp = "ACTIVE|RETIRED") String status,
    @NotNull @Valid RateRequest rate,
    @NotNull @Valid TermRequest term,
    @NotNull @Valid FeeRequest fee
) { }
