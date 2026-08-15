package com.bank.product.api;

/** A compatible active product offered to an administrator during retirement review. */
public record ProductMigrationRecommendation(
        Long productId,
        String productCode,
        String productName,
        int compatibilityScore,
        String reason) { }
