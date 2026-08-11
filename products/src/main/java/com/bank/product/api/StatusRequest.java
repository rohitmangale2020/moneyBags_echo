package com.bank.product.api;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record StatusRequest(@NotBlank @Pattern(regexp = "ACTIVE|INACTIVE") String status, @NotBlank @Size(max = 500) String reason) { }
