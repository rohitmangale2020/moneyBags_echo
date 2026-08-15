package com.bank.product.api;

/** Selected active product to receive accounts during product retirement. */
public record RetirementRequest(String migrationProductCode) { }
