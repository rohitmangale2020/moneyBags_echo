package com.bank.product.api;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProductResponse(Long productId, String productCode, String productName, String productTypeCode, String productTypeName, String productTypeDescription,
                              String description, BigDecimal minimumBalance, BigDecimal maximumBalance,
                              String currency, String status, Long versionNo, LocalDateTime createdDate,
                              LocalDateTime updatedDate, String createdBy, String updatedBy, RateRequest rate, TermRequest term, FeeRequest fee) { }
