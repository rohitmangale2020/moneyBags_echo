package com.bank.product.api;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record ProductRequest(
    @NotBlank @Size(max = 50) String productCode,
    @NotBlank @Size(max = 150) String productName,
    @NotBlank @Size(max = 30) String productTypeCode,
    @Size(max = 500) String description,
    @DecimalMin(value = "0.00", inclusive = true) BigDecimal minimumBalance,
    @DecimalMin(value = "0.00", inclusive = true) BigDecimal maximumBalance,
    @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
    @NotBlank @Pattern(regexp = "ACTIVE|RETIRED") String status,
    @NotNull @Valid RateRequest rate,
    @NotNull @Valid TermRequest term,
    @NotNull @Valid FeeRequest fee
) { }
