package com.bank.product.api;
import java.time.LocalDateTime;
public record ProductStatusHistoryResponse(String previousStatus, String newStatus, String changeReason, LocalDateTime changedDate, String changedBy) { }
