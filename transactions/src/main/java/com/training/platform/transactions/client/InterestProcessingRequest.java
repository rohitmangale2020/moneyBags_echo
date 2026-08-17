package com.training.platform.transactions.client;

import java.time.LocalDate;

public record InterestProcessingRequest(LocalDate periodEnd, String transactionRef) { }
