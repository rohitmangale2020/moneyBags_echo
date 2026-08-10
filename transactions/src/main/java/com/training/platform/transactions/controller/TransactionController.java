package com.training.platform.transactions.controller;

import com.training.platform.transactions.dto.TransactionRequest;
import com.training.platform.transactions.dto.TransactionResponse;
import com.training.platform.transactions.entity.BankTransaction;
import com.training.platform.transactions.service.BankTransactionService;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {
    private final BankTransactionService transactionService;

    public TransactionController(BankTransactionService transactionService) { this.transactionService = transactionService; }

    @GetMapping("/{transactionId}")
    public TransactionResponse getById(@PathVariable String transactionId) { return TransactionResponse.from(transactionService.getById(transactionId)); }

    @GetMapping
    public List<TransactionResponse> find(@RequestParam(required = false) String transactionRef,
                                          @RequestParam(required = false) String debitAccountId,
                                          @RequestParam(required = false) String creditAccountId) {
        if (transactionRef != null) return List.of(TransactionResponse.from(transactionService.getByReference(transactionRef)));
        if (debitAccountId != null) return transactionService.getDebitAccountTransactions(debitAccountId).stream().map(TransactionResponse::from).toList();
        if (creditAccountId != null) return transactionService.getCreditAccountTransactions(creditAccountId).stream().map(TransactionResponse::from).toList();
        throw new IllegalArgumentException("Provide transactionRef, debitAccountId, or creditAccountId");
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse initiate(@Valid @RequestBody TransactionRequest request) { return TransactionResponse.from(transactionService.initiate(toEntity(request))); }

    @PutMapping("/{transactionId}")
    public TransactionResponse update(@PathVariable String transactionId, @Valid @RequestBody TransactionRequest request) {
        return TransactionResponse.from(transactionService.update(transactionId, toEntity(request)));
    }

    private BankTransaction toEntity(TransactionRequest request) {
        BankTransaction transaction = new BankTransaction();
        transaction.setTransactionRef(request.transactionRef());
        transaction.setTransactionType(request.transactionType());
        transaction.setTransactionStatus(request.transactionStatus());
        transaction.setTransactionChannel(request.transactionChannel());
        transaction.setDebitAccountId(request.debitAccountId());
        transaction.setCreditAccountId(request.creditAccountId());
        transaction.setExternalBeneficiary(request.externalBeneficiary());
        transaction.setAmount(request.amount());
        transaction.setCurrencyCode(request.currencyCode().toUpperCase());
        transaction.setFeeAmount(request.feeAmount() == null ? BigDecimal.ZERO : request.feeAmount());
        transaction.setInitiatedByCustomerId(request.initiatedByCustomerId());
        transaction.setInitiatedByUserId(request.initiatedByUserId());
        transaction.setCompletedAt(request.completedAt());
        transaction.setFailureCode(request.failureCode());
        transaction.setFailureReason(request.failureReason());
        return transaction;
    }
}
