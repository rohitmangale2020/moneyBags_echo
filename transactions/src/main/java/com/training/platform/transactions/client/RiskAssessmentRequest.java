package com.training.platform.transactions.client;

import java.math.BigDecimal;

public record RiskAssessmentRequest(String transactionRef, String transactionType, BigDecimal amount,
                                    String currencyCode, String debitAccountId, String creditAccountId,
                                    String externalBeneficiary, String initiatedByCustomerId,
                                    BigDecimal oldBalanceOrg, BigDecimal oldBalanceDest) { }
