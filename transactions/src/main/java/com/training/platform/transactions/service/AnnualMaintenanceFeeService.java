package com.training.platform.transactions.service;

import com.training.platform.transactions.client.AccountsClient;
import com.training.platform.transactions.client.AnnualFeeAccountResponse;
import com.training.platform.transactions.entity.BankTransaction;
import com.training.platform.transactions.entity.TransactionStatus;
import com.training.platform.transactions.entity.TransactionType;
import com.training.platform.transactions.repository.BankTransactionRepository;
import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Charges a configured savings/current fee once on each account anniversary. */
@Service
public class AnnualMaintenanceFeeService {
    private static final Logger log = LoggerFactory.getLogger(AnnualMaintenanceFeeService.class);

    private final AccountsClient accountsClient;
    private final BankTransactionService transactionService;
    private final BankTransactionRepository transactionRepository;

    public AnnualMaintenanceFeeService(AccountsClient accountsClient,
                                       BankTransactionService transactionService,
                                       BankTransactionRepository transactionRepository) {
        this.accountsClient = accountsClient;
        this.transactionService = transactionService;
        this.transactionRepository = transactionRepository;
    }

    public int process(LocalDate asOf) {
        if (asOf == null) throw new IllegalArgumentException("Annual fee processing date is required");
        List<AnnualFeeAccountResponse> accounts = accountsClient.annualFeeAccounts();
        int processed = 0;
        for (AnnualFeeAccountResponse account : accounts) {
            if (!feeDue(account.openedAt(), asOf)) continue;
            try {
                BigDecimal fee = account.annualMaintenanceFee();
                if (fee == null || fee.signum() <= 0) continue;
                String reference = reference(account.accountId(), asOf.getYear());
                if (transactionRepository.findByTransactionRef(reference)
                        .filter(existing -> existing.getTransactionStatus() == TransactionStatus.COMPLETED)
                        .isPresent()) {
                    continue;
                }
                BankTransaction transaction = new BankTransaction();
                transaction.setTransactionRef(reference);
                transaction.setTransactionType(TransactionType.ANNUAL_MAINTENANCE_FEE);
                transaction.setTransactionStatus(TransactionStatus.INITIATED);
                transaction.setDebitAccountId(account.accountId());
                transaction.setAmount(fee);
                transaction.setCurrencyCode(account.currencyCode());
                transaction.setFeeAmount(fee);
                transaction.setInitiatedByCustomerId(account.customerId());
                transaction.setInitiatedByUserId("SYSTEM");
                if (transactionService.initiate(transaction).getTransactionStatus()
                        == TransactionStatus.COMPLETED) {
                    processed++;
                }
            } catch (RuntimeException exception) {
                log.error("Failed to charge annual maintenance fee for account {} on {}",
                        account.accountId(), asOf, exception);
            }
        }
        return processed;
    }

    static boolean feeDue(LocalDateTime openedAt, LocalDate asOf) {
        if (openedAt == null || asOf == null) return false;
        LocalDate opened = openedAt.toLocalDate();
        if (asOf.getYear() <= opened.getYear()) return false;
        LocalDate anniversary;
        try {
            anniversary = opened.withYear(asOf.getYear());
        } catch (DateTimeException exception) {
            anniversary = LocalDate.of(asOf.getYear(), 2, 28);
        }
        // The daily job catches up after an outage. The yearly transaction
        // reference and completed-transaction lookup prevent a duplicate debit.
        return !asOf.isBefore(anniversary);
    }

    private String reference(String accountId, int year) {
        String compactId = accountId.replace("-", "");
        compactId = compactId.substring(0, Math.min(32, compactId.length()));
        return "AF" + year + "-" + compactId;
    }
}
