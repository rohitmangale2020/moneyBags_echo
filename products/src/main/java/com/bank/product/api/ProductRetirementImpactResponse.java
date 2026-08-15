package com.bank.product.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Read-only impact assessment shown before an administrator retires a product. */
public record ProductRetirementImpactResponse(
        String productCode,
        long affectedAccountCount,
        long affectedCustomerCount,
        Map<String, Long> accountsByStatus,
        long frozenAccountCount,
        BigDecimal totalAvailableBalance,
        String riskLevel,
        List<ProductMigrationRecommendation> recommendedProducts) { }
