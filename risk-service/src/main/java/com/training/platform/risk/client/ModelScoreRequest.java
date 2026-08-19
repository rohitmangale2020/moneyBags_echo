package com.training.platform.risk.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public record ModelScoreRequest(
        @JsonProperty("transaction_ref") String transactionRef,
        @JsonProperty("transaction_type") String transactionType,
        BigDecimal amount,
        @JsonProperty("old_balance_org") BigDecimal oldBalanceOrg,
        @JsonProperty("old_balance_dest") BigDecimal oldBalanceDest,
        @JsonProperty("recipient_prior_tx_count") long recipientPriorTxCount,
        @JsonProperty("recipient_prior_amount") BigDecimal recipientPriorAmount,
        // Send ISO-8601 text, which FastAPI/Pydantic parses consistently.
        @JsonProperty("occurred_at") String occurredAt) { }
