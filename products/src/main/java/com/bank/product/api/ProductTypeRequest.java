package com.bank.product.api;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
public record ProductTypeRequest(@NotBlank @Size(max = 30) String productTypeCode,
                                 @NotBlank @Size(max = 100) String productTypeName,
                                 @Size(max = 500) String description,
                                 @NotBlank @Pattern(regexp = "ACTIVE|INACTIVE") String status) { }
